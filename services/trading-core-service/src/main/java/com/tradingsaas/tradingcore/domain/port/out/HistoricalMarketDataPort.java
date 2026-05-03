package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HistoricalMarketDataPort {
    List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to);

    default LatestPricesResult loadLatestPricesResult(List<String> symbols) {
        return LatestPricesResult.available(loadLatestPrices(symbols));
    }

    Map<String, BigDecimal> loadLatestPrices(List<String> symbols);

    boolean hasData(String symbol);

    record LatestPricesResult(boolean available, Map<String, BigDecimal> prices, String reason) {
        public LatestPricesResult {
            prices = prices == null ? Map.of() : Map.copyOf(prices);
        }

        public static LatestPricesResult available(Map<String, BigDecimal> prices) {
            return new LatestPricesResult(true, prices, null);
        }

        public static LatestPricesResult unavailable(String reason) {
            return new LatestPricesResult(false, Map.of(), reason == null || reason.isBlank() ? "unknown" : reason);
        }
    }
}
