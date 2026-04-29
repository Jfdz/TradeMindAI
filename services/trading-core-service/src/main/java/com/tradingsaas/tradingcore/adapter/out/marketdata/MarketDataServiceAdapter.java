package com.tradingsaas.tradingcore.adapter.out.marketdata;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

@Component
public class MarketDataServiceAdapter implements HistoricalMarketDataPort {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final WebClient webClient;
    private final String internalSecret;

    public MarketDataServiceAdapter(
            @Value("${services.market-data.url:http://localhost:8081}") String baseUrl,
            @Value("${services.market-data.internal-secret:}") String internalSecret) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    MarketDataServiceAdapter(WebClient webClient, String internalSecret) {
        this.webClient = webClient;
        this.internalSecret = internalSecret;
    }

    @Override
    public List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        PriceHistoryResponse response = webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/{ticker}/history")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("size", 1000)
                        .build(symbol))
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        res -> Mono.empty()
                )
                .bodyToMono(PriceHistoryResponse.class)
                .defaultIfEmpty(new PriceHistoryResponse(List.of()))
                .block();

        if (response == null || response.content() == null) {
            return List.of();
        }

        return response.content().stream()
                .map(p -> new OhlcvBar(
                        p.date().atStartOfDay().toInstant(ZoneOffset.UTC),
                        p.ohlcv().open(),
                        p.ohlcv().high(),
                        p.ohlcv().low(),
                        p.ohlcv().close(),
                        p.ohlcv().volume()))
                .toList();
    }

    @Override
    public Map<String, BigDecimal> loadLatestPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }

        LatestPricesResponse response = webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/latest")
                        .queryParam("tickers", symbols.toArray())
                        .build())
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        res -> Mono.empty()
                )
                .bodyToMono(LatestPricesResponse.class)
                .defaultIfEmpty(new LatestPricesResponse(List.of()))
                .block();

        if (response == null || response.prices() == null) {
            return Map.of();
        }

        return response.prices().stream()
                .collect(java.util.stream.Collectors.toMap(
                        MarketPriceResponse::ticker,
                        entry -> BigDecimal.valueOf(entry.ohlcv().close()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    @Override
    public boolean hasData(String symbol) {
        return Boolean.TRUE.equals(webClient.get()
                .uri("/api/v1/prices/{ticker}/latest", symbol)
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(String.class)
                .map(body -> true)
                .defaultIfEmpty(false)
                .block());
    }

    public Optional<MarketPriceResponse> fetchLatestPrice(String ticker, String timeframe) {
        MarketPriceResponse response = webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/{ticker}/latest")
                        .queryParam("timeframe", timeframe)
                        .build(ticker))
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(MarketPriceResponse.class)
                .block();
        return Optional.ofNullable(response);
    }

    public LatestPricesResponse fetchLatestPrices(List<String> tickers, String timeframe) {
        if (tickers == null || tickers.isEmpty()) {
            return new LatestPricesResponse(List.of());
        }
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/latest")
                        .queryParam("tickers", tickers.toArray())
                        .queryParam("timeframe", timeframe)
                        .build())
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(LatestPricesResponse.class)
                .defaultIfEmpty(new LatestPricesResponse(List.of()))
                .block();
    }

    public MarketDataPage<MarketPriceResponse> fetchHistoricalPrices(
            String ticker,
            String timeframe,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/{ticker}/history")
                        .queryParam("timeframe", timeframe)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(ticker))
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(MarketPricePageResponse.class)
                .defaultIfEmpty(new MarketPricePageResponse(List.of(), page, size, 0, 0))
                .block();
    }

    public MarketDataPage<MarketSymbolResponse> fetchSymbols(int page, int size) {
        return webClient.get()
                .uri(uri -> uri.path("/api/v1/symbols")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .headers(this::addInternalSecret)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(MarketSymbolPageResponse.class)
                .defaultIfEmpty(new MarketSymbolPageResponse(List.of(), page, size, 0, 0))
                .block();
    }

    private void addInternalSecret(org.springframework.http.HttpHeaders headers) {
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface MarketDataPage<T> {
        List<T> content();
        int page();
        int size();
        long totalElements();
        int totalPages();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketPricePageResponse(
            List<MarketPriceResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) implements MarketDataPage<MarketPriceResponse> {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketSymbolPageResponse(
            List<MarketSymbolResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) implements MarketDataPage<MarketSymbolResponse> {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceHistoryResponse(List<PriceEntry> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LatestPricesResponse(List<MarketPriceResponse> prices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceEntry(String ticker, LocalDate date, Ohlcv ohlcv) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketPriceResponse(String ticker, LocalDate date, String timeFrame, Ohlcv ohlcv, BigDecimal adjustedClose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketSymbolResponse(String ticker, String name, String exchange, String sector, boolean active) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ohlcv(double open, double high, double low, double close, long volume) {}
}
