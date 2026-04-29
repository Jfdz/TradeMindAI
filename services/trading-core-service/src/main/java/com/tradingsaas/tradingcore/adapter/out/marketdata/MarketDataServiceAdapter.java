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
import reactor.core.publisher.Mono;

@Component
public class MarketDataServiceAdapter implements HistoricalMarketDataPort {

    private final WebClient webClient;

    public MarketDataServiceAdapter(
            @Value("${services.market-data.url:http://localhost:8081}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to) {
        PriceHistoryResponse response = webClient.get()
                .uri(uri -> uri.path("/api/v1/prices/{ticker}/history")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("size", 1000)
                        .build(symbol))
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
                        .queryParam("tickers", String.join(",", symbols))
                        .build())
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
                        LatestPriceEntry::ticker,
                        entry -> BigDecimal.valueOf(entry.ohlcv().close()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    @Override
    public boolean hasData(String symbol) {
        return Boolean.TRUE.equals(webClient.get()
                .uri("/api/v1/prices/{ticker}/latest", symbol)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), res -> Mono.empty())
                .bodyToMono(String.class)
                .map(body -> true)
                .defaultIfEmpty(false)
                .block());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceHistoryResponse(List<PriceEntry> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LatestPricesResponse(List<LatestPriceEntry> prices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceEntry(String ticker, LocalDate date, Ohlcv ohlcv) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LatestPriceEntry(String ticker, LocalDate date, Ohlcv ohlcv) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ohlcv(double open, double high, double low, double close, long volume) {}
}
