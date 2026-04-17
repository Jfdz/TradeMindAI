package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.time.LocalDate;
import java.util.List;

public interface HistoricalMarketDataPort {
    List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to);
}
