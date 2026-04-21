package com.tradingsaas.marketdata.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class YahooFinanceAdapter implements MarketDataProvider {

    private final WebClient webClient;

    public YahooFinanceAdapter(WebClient yahooFinanceWebClient) {
        this.webClient = Objects.requireNonNull(yahooFinanceWebClient, "yahooFinanceWebClient must not be null");
    }

    @Override
    public List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeFrame, "timeFrame must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }

        YahooChartResponse response = webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("interval", timeFrame.apiValue())
                        .queryParam("period1", from.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                        .queryParam("period2", to.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                        .queryParam("includePrePost", false)
                        .queryParam("events", "div,splits")
                        .queryParam("includeAdjustedClose", true)
                        .build(symbol.ticker()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();

        if (response == null || response.chart == null || response.chart.result == null || response.chart.result.isEmpty()) {
            throw new IllegalStateException("Yahoo Finance returned an empty chart response");
        }

        YahooChartResult result = response.chart.result.get(0);
        YahooQuote quote = result.indicators == null || result.indicators.quote.isEmpty()
                ? null
                : result.indicators.quote.get(0);
        if (quote == null || result.timestamp == null || result.timestamp.isEmpty()) {
            throw new IllegalStateException("Yahoo Finance response does not contain price data");
        }

        List<StockPrice> prices = new ArrayList<>();
        List<Double> adjClose = result.indicators != null && !result.indicators.adjClose.isEmpty()
                ? result.indicators.adjClose.get(0).adjclose
                : List.of();

        for (int i = 0; i < result.timestamp.size(); i++) {
            Double open = valueAt(quote.open, i);
            Double high = valueAt(quote.high, i);
            Double low = valueAt(quote.low, i);
            Double close = valueAt(quote.close, i);
            Long volume = longAt(quote.volume, i);
            if (open == null || high == null || low == null || close == null || volume == null) {
                continue;
            }

            BigDecimal adjustedClose = adjClose.size() > i && adjClose.get(i) != null
                    ? BigDecimal.valueOf(adjClose.get(i))
                    : BigDecimal.valueOf(close);
            LocalDate date = Instant.ofEpochSecond(result.timestamp.get(i)).atZone(ZoneOffset.UTC).toLocalDate();

            prices.add(new StockPrice(
                    symbol,
                    date,
                    timeFrame,
                    new OHLCV(
                            BigDecimal.valueOf(open),
                            BigDecimal.valueOf(high),
                            BigDecimal.valueOf(low),
                            BigDecimal.valueOf(close),
                            volume),
                    adjustedClose));
        }

        return prices;
    }

    private static Double valueAt(List<Double> values, int index) {
        return values != null && values.size() > index ? values.get(index) : null;
    }

    private static Long longAt(List<Long> values, int index) {
        return values != null && values.size() > index ? values.get(index) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooChartResponse(YahooChart chart) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooChart(List<YahooChartResult> result, Object error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooChartResult(List<Long> timestamp, YahooIndicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooIndicators(List<YahooQuote> quote, @JsonProperty("adjclose") List<YahooAdjClose> adjClose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooQuote(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record YahooAdjClose(List<Double> adjclose) {}
}
