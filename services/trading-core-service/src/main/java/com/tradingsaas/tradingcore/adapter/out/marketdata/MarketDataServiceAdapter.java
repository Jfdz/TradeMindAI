package com.tradingsaas.tradingcore.adapter.out.marketdata;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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
                .bodyToMono(PriceHistoryResponse.class)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceHistoryResponse(List<PriceEntry> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PriceEntry(String ticker, LocalDate date, Ohlcv ohlcv) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ohlcv(double open, double high, double low, double close, long volume) {}
}
