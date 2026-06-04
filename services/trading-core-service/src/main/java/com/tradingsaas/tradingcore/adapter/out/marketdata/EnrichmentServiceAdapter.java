package com.tradingsaas.tradingcore.adapter.out.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.netty.channel.ChannelOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class EnrichmentServiceAdapter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final ParameterizedTypeReference<List<NewsItemResponse>> NEWS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<EarningsEventResponse>> EARNINGS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<AnalystRecommendationResponse>> RECS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> STRINGS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String internalSecret;

    @Autowired
    public EnrichmentServiceAdapter(
            @Value("${services.market-data.url:http://localhost:8081}") String baseUrl,
            @Value("${services.market-data.internal-secret:}") String internalSecret,
            @Value("${services.market-data.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${services.market-data.response-timeout-seconds:10}") int responseTimeoutSeconds) {
        this(WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                        .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))))
                .build(), internalSecret);
    }

    EnrichmentServiceAdapter(WebClient webClient, String internalSecret) {
        this.webClient = webClient;
        this.internalSecret = internalSecret;
    }

    public Optional<CompanyProfileResponse> fetchProfile(String ticker) {
        return Optional.ofNullable(webClient.get()
                .uri("/api/v1/enrichment/profile/{ticker}", ticker)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(CompanyProfileResponse.class)
                .block());
    }

    public List<NewsItemResponse> fetchMarketNews(String category, int limit) {
        List<NewsItemResponse> result = webClient.get()
                .uri(uri -> uri.path("/api/v1/enrichment/news")
                        .queryParam("category", category)
                        .queryParam("limit", limit)
                        .build())
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(NEWS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public List<NewsItemResponse> fetchTickerNews(String ticker, Instant from, Instant to, int limit) {
        List<NewsItemResponse> result = webClient.get()
                .uri(uri -> uri.path("/api/v1/enrichment/news/{ticker}")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .queryParam("limit", limit)
                        .build(ticker))
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(NEWS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public List<NewsItemResponse> fetchAggregatedTickerNews(String ticker, Instant from, Instant to, int limit) {
        List<NewsItemResponse> result = webClient.get()
                .uri(uri -> uri.path("/api/v1/news-aggregated/{ticker}")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .queryParam("limit", limit)
                        .build(ticker))
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(NEWS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public List<EarningsEventResponse> fetchEarnings(String ticker) {
        List<EarningsEventResponse> result = webClient.get()
                .uri("/api/v1/enrichment/earnings/{ticker}", ticker)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(EARNINGS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public List<AnalystRecommendationResponse> fetchRecommendations(String ticker) {
        List<AnalystRecommendationResponse> result = webClient.get()
                .uri("/api/v1/enrichment/recommendations/{ticker}", ticker)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(RECS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public List<String> fetchPeers(String ticker) {
        List<String> result = webClient.get()
                .uri("/api/v1/enrichment/peers/{ticker}", ticker)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(STRINGS_LIST_TYPE)
                .defaultIfEmpty(List.of())
                .block();
        return result != null ? result : List.of();
    }

    public Optional<InsiderActivityResponse> fetchInsiderActivity(String ticker) {
        return Optional.ofNullable(webClient.get()
                .uri("/api/v1/enrichment/insider/{ticker}", ticker)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(InsiderActivityResponse.class)
                .block());
    }

    private void addInternalSecret(org.springframework.http.HttpHeaders headers) {
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyProfileResponse(
            String ticker,
            String name,
            String logo,
            String country,
            String currency,
            String exchange,
            LocalDate ipo,
            BigDecimal marketCap,
            String phone,
            String weburl,
            String industry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewsItemResponse(
            long id,
            String headline,
            String publishedAt,
            String category,
            String source,
            String summary,
            String url,
            String image) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarningsEventResponse(
            String ticker,
            LocalDate period,
            int year,
            int quarter,
            BigDecimal epsActual,
            BigDecimal epsEstimate,
            BigDecimal revenueActual,
            BigDecimal revenueEstimate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalystRecommendationResponse(
            String ticker,
            LocalDate period,
            int buy,
            int hold,
            int sell,
            int strongBuy,
            int strongSell) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InsiderActivityResponse(
            String ticker,
            int buyCount,
            int sellCount,
            long netShares) {}
}
