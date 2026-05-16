package com.tradingsaas.marketdata.enrichment.adapter.out.external;

import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.port.out.NewsProviderPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Secondary news provider that reads Yahoo Finance's public RSS feed.
 *
 * <p>Strictly headlines only: title, link, pubDate, source, description.
 * No HTML scraping, no full-body extraction, no internal selector parsing.
 *
 * <p>Registered only when {@code market-data.yahoo-rss.enabled=true} via
 * {@code YahooRssWebClientConfig}.
 */
@Component
@ConditionalOnBean(name = "yahooRssWebClient")
public class YahooRssNewsAdapter implements NewsProviderPort {

    public static final String PROVIDER_NAME = "yahoo-rss";
    public static final int PROVIDER_PRIORITY = 20;

    private static final Logger log = LoggerFactory.getLogger(YahooRssNewsAdapter.class);
    // Strict RFC 1123 rejects malformed day-of-week prefixes ("Mon, 12 May 2026"
    // when that date is a Tuesday). We strip the prefix and parse the rest
    // leniently so a single mismatched day in a feed does not lose the item.
    private static final DateTimeFormatter PUB_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    public YahooRssNewsAdapter(WebClient yahooRssWebClient, MeterRegistry meterRegistry) {
        this.webClient = Objects.requireNonNull(yahooRssWebClient, "yahooRssWebClient must not be null");
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public int priority() {
        return PROVIDER_PRIORITY;
    }

    @Override
    public List<NewsItem> fetchTickerNews(String ticker, Instant from, Instant to, int limit) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        try {
            byte[] body = webClient.get()
                    .uri(b -> b.path("/rss/2.0/headline")
                            .queryParam("s", ticker)
                            .queryParam("region", "US")
                            .queryParam("lang", "en-US")
                            .build())
                    .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (body == null || body.length == 0) {
                record("ticker_news", "empty");
                return List.of();
            }
            List<NewsItem> all = parse(body);
            List<NewsItem> filtered = all.stream()
                    .filter(n -> !n.publishedAt().isBefore(from) && !n.publishedAt().isAfter(to))
                    .sorted((a, b) -> b.publishedAt().compareTo(a.publishedAt()))
                    .limit(Math.max(0, limit))
                    .toList();
            record("ticker_news", "ok");
            return filtered;
        } catch (WebClientResponseException e) {
            record("ticker_news", "upstream_" + e.getStatusCode().value());
            log.warn(
                    "event=yahoo_rss.ticker_news.upstream_error ticker={} status={}",
                    ticker,
                    e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        } catch (Exception e) {
            record("ticker_news", "parse_error");
            log.warn("event=yahoo_rss.ticker_news.parse_error ticker={} message={}", ticker, e.getMessage());
            throw new EnrichmentUnavailableException(ticker, "parse_error", e);
        }
    }

    /**
     * Yahoo's public RSS feed has no concept of a market-wide news category;
     * we return empty rather than fabricate a response. {@code NewsAggregatorService}
     * already tolerates per-provider empties.
     */
    @Override
    public List<NewsItem> fetchMarketNews(String category, int limit) {
        return List.of();
    }

    private List<NewsItem> parse(byte[] body) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        SAXParser parser = factory.newSAXParser();
        RssHandler handler = new RssHandler();
        parser.parse(new InputSource(new ByteArrayInputStream(body)), handler);
        return handler.items;
    }

    private void record(String endpoint, String outcome) {
        Counter.builder("yahoo_rss_requests_total")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private static final class RssHandler extends DefaultHandler {
        private final List<NewsItem> items = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();
        private boolean inItem;
        private String title;
        private String link;
        private String description;
        private String pubDate;
        private String sourceName;
        private String thumbnailUrl;
        private int thumbnailWidth;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            buffer.setLength(0);
            if ("item".equals(qName)) {
                inItem = true;
                title = null;
                link = null;
                description = null;
                pubDate = null;
                sourceName = null;
                thumbnailUrl = null;
                thumbnailWidth = -1;
                return;
            }
            if (!inItem) {
                return;
            }
            // Yahoo Finance items publish images via three optional elements.
            // Capture the URL whenever any of them appears; prefer the highest
            // declared width when multiple media:thumbnail entries exist.
            switch (qName) {
                case "media:thumbnail", "thumbnail" -> {
                    String url = attributes.getValue("url");
                    if (url != null && !url.isBlank()) {
                        int width = parseIntOrDefault(attributes.getValue("width"), 0);
                        if (thumbnailUrl == null || width > thumbnailWidth) {
                            thumbnailUrl = url.trim();
                            thumbnailWidth = width;
                        }
                    }
                }
                case "media:content", "content" -> {
                    String url = attributes.getValue("url");
                    String medium = attributes.getValue("medium");
                    String type = attributes.getValue("type");
                    boolean looksLikeImage = "image".equalsIgnoreCase(medium)
                            || (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/"));
                    if (url != null && !url.isBlank() && looksLikeImage && thumbnailUrl == null) {
                        thumbnailUrl = url.trim();
                    }
                }
                case "enclosure" -> {
                    String url = attributes.getValue("url");
                    String type = attributes.getValue("type");
                    if (url != null && !url.isBlank() && type != null
                            && type.toLowerCase(Locale.ROOT).startsWith("image/")
                            && thumbnailUrl == null) {
                        thumbnailUrl = url.trim();
                    }
                }
                default -> { /* no-op */ }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            buffer.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!inItem) {
                return;
            }
            String text = buffer.toString().trim();
            switch (qName) {
                case "title" -> title = text;
                case "link" -> link = text;
                case "description" -> description = text;
                case "pubDate" -> pubDate = text;
                case "source" -> sourceName = text;
                case "item" -> {
                    inItem = false;
                    if (title != null && link != null && pubDate != null) {
                        Instant published = parsePubDate(pubDate);
                        if (published != null) {
                            long id = stableId(link);
                            items.add(new NewsItem(
                                    id,
                                    title,
                                    published,
                                    null,
                                    sourceName != null ? sourceName : "Yahoo Finance",
                                    description,
                                    link,
                                    thumbnailUrl));
                        }
                    }
                }
                default -> {}
            }
            buffer.setLength(0);
        }

        private static int parseIntOrDefault(String value, int fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static Instant parsePubDate(String value) {
            try {
                String stripped = value.replaceFirst("^[A-Za-z]+,\\s*", "").trim();
                return ZonedDateTime.parse(stripped, PUB_DATE).toInstant();
            } catch (Exception e) {
                return null;
            }
        }

        private static long stableId(String url) {
            byte[] bytes = url.getBytes(StandardCharsets.UTF_8);
            long h = 1125899906842597L;
            for (byte b : bytes) {
                h = 31 * h + b;
            }
            return Math.abs(h);
        }
    }
}
