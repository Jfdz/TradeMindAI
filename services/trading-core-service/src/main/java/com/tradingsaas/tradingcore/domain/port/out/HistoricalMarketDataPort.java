package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HistoricalMarketDataPort {
    List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to);
    Map<String, BigDecimal> loadLatestPrices(List<String> symbols);
    boolean hasData(String symbol);
}
