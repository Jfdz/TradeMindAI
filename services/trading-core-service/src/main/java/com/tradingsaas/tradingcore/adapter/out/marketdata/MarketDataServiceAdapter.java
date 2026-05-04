package com.tradingsaas.tradingcore.adapter.out.marketdata;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

@Component
public class MarketDataServiceAdapter implements HistoricalMarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(MarketDataServiceAdapter.class);
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final int FALLBACK_LOOKBACK_DAYS = 14;

    private final WebClient webClient;
    private final String internalSecret;
    private final Clock clock;

    @Autowired
    public MarketDataServiceAdapter(
            @Value("${services.market-data.url:http://localhost:8081}") String baseUrl,
            @Value("${services.market-data.internal-secret:}") String internalSecret) {
        this(WebClient.builder().baseUrl(baseUrl).build(), internalSecret, Clock.systemUTC());
    }

    MarketDataServiceAdapter(WebClient webClient, String internalSecret, Clock clock) {
        this.webClient = webClient;
        this.internalSecret = internalSecret;
        this.clock = clock;
    }

    MarketDataServiceAdapter(WebClient webClient, String internalSecret) {
        this(webClient, internalSecret, Clock.systemUTC());
    }

    @PostConstruct
    void logInternalSecretConfiguration() {
        log.info(
                "Market data internal secret configured: length={}",
                internalSecret == null ? 0 : internalSecret.length());
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
        return loadLatestPricesResult(symbols).prices();
    }

    @Override
    public LatestPricesResult loadLatestPricesResult(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return LatestPricesResult.available(Map.of());
        }

        LatestPricesResult latestResult = fetchLatestPricesFromBatchEndpoint(symbols);
        Map<String, BigDecimal> resolvedPrices = new LinkedHashMap<>(latestResult.prices());

        List<String> missingSymbols = symbols.stream()
                .filter(s -> !resolvedPrices.containsKey(s))
                .toList();

        if (!missingSymbols.isEmpty()) {
            for (String symbol : missingSymbols) {
                BigDecimal fallbackPrice = fetchLatestPriceFromHistory(symbol);
                if (fallbackPrice != null) {
                    resolvedPrices.put(symbol, fallbackPrice);
                    log.debug("Recovered price for {} from history fallback", symbol);
                }
            }
        }

        List<String> stillMissing = symbols.stream()
                .filter(s -> !resolvedPrices.containsKey(s))
                .toList();

        if (stillMissing.size() == symbols.size()) {
            return LatestPricesResult.unavailable(latestResult.reason() != null
                    ? latestResult.reason()
                    : "no price data available");
        }

        if (!stillMissing.isEmpty()) {
            return LatestPricesResult.partial(resolvedPrices, stillMissing);
        }

        return LatestPricesResult.available(resolvedPrices);
    }

    private LatestPricesResult fetchLatestPricesFromBatchEndpoint(List<String> symbols) {
        try {
            return webClient.get()
                    .uri(uri -> uri.path("/api/v1/prices/latest")
                            .queryParam("symbols", symbols.toArray())
                            .queryParam("timeframe", "DAILY")
                            .build())
                    .headers(this::addInternalSecret)
                    .exchangeToMono(response -> {
                        if (!response.statusCode().is2xxSuccessful()) {
                            return response.releaseBody()
                                    .thenReturn(LatestPricesResult.unavailable(
                                            "HTTP " + response.statusCode().value()));
                        }
                        return response.bodyToMono(LatestPricesResponse.class)
                                .map(this::toLatestPricesResult)
                                .defaultIfEmpty(LatestPricesResult.unavailable("empty response"));
                    })
                    .onErrorResume(e -> Mono.just(LatestPricesResult.unavailable(e.getClass().getSimpleName())))
                    .blockOptional()
                    .orElseGet(() -> LatestPricesResult.unavailable("no response"));
        } catch (Exception e) {
            return LatestPricesResult.unavailable(e.getClass().getSimpleName());
        }
    }

    private BigDecimal fetchLatestPriceFromHistory(String symbol) {
        LocalDate toDate = LocalDate.now(clock);
        LocalDate fromDate = toDate.minusDays(FALLBACK_LOOKBACK_DAYS);

        try {
            MarketDataPage<MarketPriceResponse> historyResponse = fetchHistoricalPrices(
                    symbol, "DAILY", fromDate, toDate, 0, 1);

            if (historyResponse == null || historyResponse.content() == null
                    || historyResponse.content().isEmpty()) {
                return null;
            }

            MarketPriceResponse mostRecent = historyResponse.content().getFirst();
            return extractPrice(mostRecent);
        } catch (Exception e) {
            log.debug("Failed to fetch history for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal extractPrice(MarketPriceResponse entry) {
        if (entry == null) {
            return null;
        }
        if (entry.adjustedClose() != null) {
            return entry.adjustedClose();
        }
        return entry.ohlcv() != null ? BigDecimal.valueOf(entry.ohlcv().close()) : null;
    }

    private LatestPricesResult toLatestPricesResult(LatestPricesResponse response) {
        if (response == null || response.prices() == null) {
            return LatestPricesResult.unavailable("missing prices");
        }
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (MarketPriceResponse entry : response.prices()) {
            BigDecimal price = extractPrice(entry);
            if (entry.ticker() != null && price != null) {
                prices.put(entry.ticker(), price);
            }
        }
        return LatestPricesResult.available(prices);
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
                        .queryParam("symbols", tickers.toArray())
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
