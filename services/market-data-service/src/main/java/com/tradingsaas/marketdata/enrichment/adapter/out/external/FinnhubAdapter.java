package com.tradingsaas.marketdata.enrichment.adapter.out.external;

import com.tradingsaas.marketdata.enrichment.adapter.out.external.FinnhubDtos.EarningsDto;
import com.tradingsaas.marketdata.enrichment.adapter.out.external.FinnhubDtos.NewsDto;
import com.tradingsaas.marketdata.enrichment.adapter.out.external.FinnhubDtos.ProfileDto;
import com.tradingsaas.marketdata.enrichment.adapter.out.external.FinnhubDtos.RecommendationDto;
import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class FinnhubAdapter implements MarketEnrichmentProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubAdapter.class);
    private static final String TOKEN_HEADER = "X-Finnhub-Token";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WebClient webClient;
    private final String apiKey;
    private final MeterRegistry meterRegistry;

    public FinnhubAdapter(
            WebClient finnhubWebClient,
            @Value("${market-data.finnhub.api-key:}") String apiKey,
            MeterRegistry meterRegistry) {
        this.webClient = Objects.requireNonNull(finnhubWebClient, "finnhubWebClient must not be null");
        this.apiKey = apiKey;
        this.meterRegistry = meterRegistry;
    }

    private void record(String endpoint, String outcome) {
        Counter.builder("finnhub_requests_total")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    @Override
    public CompanyProfile fetchProfile(String ticker) {
        try {
            ProfileDto dto = webClient.get()
                    .uri(b -> b.path("/stock/profile2").queryParam("symbol", ticker).build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ProfileDto.class)
                    .block();

            if (dto == null || dto.ticker() == null || dto.name() == null) {
                throw new IllegalStateException("Finnhub returned empty profile for ticker: " + ticker);
            }
            if (dto.logo() == null || dto.logo().isBlank()) {
                log.info("event=finnhub.profile.no_logo ticker={}", ticker);
                record("profile", "no_logo");
            } else {
                record("profile", "ok");
            }
            return new CompanyProfile(
                    dto.ticker(),
                    dto.name(),
                    dto.logo(),
                    dto.country(),
                    dto.currency(),
                    dto.exchange(),
                    parseLocalDate(dto.ipo()),
                    dto.marketCap() != null ? BigDecimal.valueOf(dto.marketCap()) : null,
                    dto.phone(),
                    dto.weburl(),
                    dto.industry());

        } catch (WebClientResponseException.Unauthorized e) {
            record("profile", "unauthorized");
            log.error("event=finnhub.profile.unauthorized ticker={} key_present={}", ticker, !apiKey.isBlank());
            throw new EnrichmentUnavailableException(ticker, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("profile", "rate_limited");
            log.warn("event=finnhub.profile.rate_limited ticker={} retryAfter={}",
                    ticker, e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(ticker, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("profile", "upstream_error");
            log.warn("event=finnhub.profile.upstream_error ticker={} status={}", ticker, e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    @Override
    public List<NewsItem> fetchMarketNews(String category, int limit) {
        try {
            List<NewsDto> dtos = webClient.get()
                    .uri(b -> b.path("/news").queryParam("category", category).build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<NewsDto>>() {})
                    .block();

            record("market_news", "ok");
            return dtos == null ? List.of() : dtos.stream().limit(limit).map(this::toNewsItem).toList();
        } catch (WebClientResponseException.Unauthorized e) {
            record("market_news", "unauthorized");
            log.error("event=finnhub.market_news.unauthorized key_present={}", !apiKey.isBlank());
            throw new EnrichmentUnavailableException(category, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("market_news", "rate_limited");
            log.warn("event=finnhub.market_news.rate_limited retryAfter={}", e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(category, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("market_news", "upstream_error");
            log.warn("event=finnhub.market_news.upstream_error status={}", e.getStatusCode().value());
            throw new EnrichmentUnavailableException(category, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    @Override
    public List<NewsItem> fetchTickerNews(String ticker, Instant from, Instant to, int limit) {
        String fromDate = DATE_FMT.format(from.atZone(ZoneOffset.UTC).toLocalDate());
        String toDate = DATE_FMT.format(to.atZone(ZoneOffset.UTC).toLocalDate());
        try {
            List<NewsDto> dtos = webClient.get()
                    .uri(b -> b.path("/company-news")
                            .queryParam("symbol", ticker)
                            .queryParam("from", fromDate)
                            .queryParam("to", toDate)
                            .build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<NewsDto>>() {})
                    .block();

            record("ticker_news", "ok");
            return dtos == null ? List.of() : dtos.stream().limit(limit).map(this::toNewsItem).toList();
        } catch (WebClientResponseException.Unauthorized e) {
            record("ticker_news", "unauthorized");
            log.error("event=finnhub.ticker_news.unauthorized ticker={} key_present={}", ticker, !apiKey.isBlank());
            throw new EnrichmentUnavailableException(ticker, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("ticker_news", "rate_limited");
            log.warn("event=finnhub.ticker_news.rate_limited ticker={} retryAfter={}",
                    ticker, e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(ticker, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("ticker_news", "upstream_error");
            log.warn("event=finnhub.ticker_news.upstream_error ticker={} status={}", ticker, e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    @Override
    public List<EarningsEvent> fetchEarnings(String ticker) {
        try {
            List<EarningsDto> dtos = webClient.get()
                    .uri(b -> b.path("/stock/earnings").queryParam("symbol", ticker).build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<EarningsDto>>() {})
                    .block();

            record("earnings", "ok");
            return dtos == null ? List.of() : dtos.stream().map(this::toEarningsEvent).toList();
        } catch (WebClientResponseException.Unauthorized e) {
            record("earnings", "unauthorized");
            log.error("event=finnhub.earnings.unauthorized ticker={} key_present={}", ticker, !apiKey.isBlank());
            throw new EnrichmentUnavailableException(ticker, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("earnings", "rate_limited");
            log.warn("event=finnhub.earnings.rate_limited ticker={} retryAfter={}",
                    ticker, e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(ticker, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("earnings", "upstream_error");
            log.warn("event=finnhub.earnings.upstream_error ticker={} status={}", ticker, e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    @Override
    public List<AnalystRecommendation> fetchRecommendations(String ticker) {
        try {
            List<RecommendationDto> dtos = webClient.get()
                    .uri(b -> b.path("/stock/recommendation").queryParam("symbol", ticker).build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<RecommendationDto>>() {})
                    .block();

            record("recommendations", "ok");
            return dtos == null ? List.of() : dtos.stream().map(this::toRecommendation).toList();
        } catch (WebClientResponseException.Unauthorized e) {
            record("recommendations", "unauthorized");
            log.error("event=finnhub.recommendations.unauthorized ticker={} key_present={}", ticker, !apiKey.isBlank());
            throw new EnrichmentUnavailableException(ticker, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("recommendations", "rate_limited");
            log.warn("event=finnhub.recommendations.rate_limited ticker={} retryAfter={}",
                    ticker, e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(ticker, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("recommendations", "upstream_error");
            log.warn("event=finnhub.recommendations.upstream_error ticker={} status={}", ticker, e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    @Override
    public List<String> fetchPeers(String ticker) {
        try {
            List<String> peers = webClient.get()
                    .uri(b -> b.path("/stock/peers").queryParam("symbol", ticker).build())
                    .header(TOKEN_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                    .block();

            record("peers", "ok");
            return peers != null ? peers : List.of();
        } catch (WebClientResponseException.Unauthorized e) {
            record("peers", "unauthorized");
            log.error("event=finnhub.peers.unauthorized ticker={} key_present={}", ticker, !apiKey.isBlank());
            throw new EnrichmentUnavailableException(ticker, "unauthorized", e);
        } catch (WebClientResponseException.TooManyRequests e) {
            record("peers", "rate_limited");
            log.warn("event=finnhub.peers.rate_limited ticker={} retryAfter={}",
                    ticker, e.getHeaders().getFirst("Retry-After"));
            throw new EnrichmentUnavailableException(ticker, "rate_limited", e);
        } catch (WebClientResponseException e) {
            record("peers", "upstream_error");
            log.warn("event=finnhub.peers.upstream_error ticker={} status={}", ticker, e.getStatusCode().value());
            throw new EnrichmentUnavailableException(ticker, "upstream_" + e.getStatusCode().value(), e);
        }
    }

    public boolean isApiKeyPresent() {
        return !apiKey.isBlank();
    }

    private NewsItem toNewsItem(NewsDto dto) {
        return new NewsItem(
                dto.id(),
                dto.headline(),
                Instant.ofEpochSecond(dto.datetime()),
                dto.category(),
                dto.source(),
                dto.summary(),
                dto.url(),
                dto.image());
    }

    private EarningsEvent toEarningsEvent(EarningsDto dto) {
        return new EarningsEvent(
                dto.ticker(),
                LocalDate.parse(dto.period()),
                dto.year(),
                dto.quarter(),
                dto.epsActual() != null ? BigDecimal.valueOf(dto.epsActual()) : null,
                dto.epsEstimate() != null ? BigDecimal.valueOf(dto.epsEstimate()) : null,
                null,
                null);
    }

    private AnalystRecommendation toRecommendation(RecommendationDto dto) {
        return new AnalystRecommendation(
                dto.ticker(),
                LocalDate.parse(dto.period()),
                dto.buy(),
                dto.hold(),
                dto.sell(),
                dto.strongBuy(),
                dto.strongSell());
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
