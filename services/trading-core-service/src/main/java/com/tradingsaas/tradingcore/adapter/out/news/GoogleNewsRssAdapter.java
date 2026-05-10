package com.tradingsaas.tradingcore.adapter.out.news;

import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GoogleNewsRssAdapter implements NewsContextProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsRssAdapter.class);
    private static final int MAX_HEADLINES = 10;
    private static final Duration MAX_AGE = Duration.ofHours(48);
    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private final WebClient webClient;
    private final int timeoutSeconds;

    @Autowired
    public GoogleNewsRssAdapter(
            @Value("${trading-core.news.google-rss.base-url:https://news.google.com}") String baseUrl,
            @Value("${trading-core.news.google-rss.timeout-seconds:8}") int timeoutSeconds) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeoutSeconds = timeoutSeconds;
    }

    GoogleNewsRssAdapter(WebClient webClient, int timeoutSeconds) {
        this.webClient = webClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public List<NewsHeadline> fetchHeadlines(String ticker) {
        try {
            String rss = webClient.get()
                    .uri(u -> u.path("/rss/search")
                            .queryParam("q", ticker + " stock")
                            .queryParam("hl", "en")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            if (rss == null || rss.isBlank()) return List.of();
            return parseRss(rss);
        } catch (Exception e) {
            log.warn("Google News RSS fetch failed for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    private List<NewsHeadline> parseRss(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagName("item");
            Instant cutoff = Instant.now().minus(MAX_AGE);
            List<NewsHeadline> headlines = new ArrayList<>();
            for (int i = 0; i < items.getLength() && headlines.size() < MAX_HEADLINES; i++) {
                Element item = (Element) items.item(i);
                String title = textOf(item, "title");
                String pubDateStr = textOf(item, "pubDate");
                String source = sourceOf(item);
                if (title == null || pubDateStr == null) continue;
                Instant publishedAt = parseDate(pubDateStr);
                if (publishedAt == null || publishedAt.isBefore(cutoff)) continue;
                headlines.add(new NewsHeadline(title, source != null ? source : "Google News", publishedAt));
            }
            return headlines;
        } catch (Exception e) {
            log.warn("RSS parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    private String sourceOf(Element item) {
        NodeList sources = item.getElementsByTagName("source");
        return sources.getLength() > 0 ? sources.item(0).getTextContent() : null;
    }

    private Instant parseDate(String raw) {
        try {
            return ZonedDateTime.parse(raw.trim(), RFC_822).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
