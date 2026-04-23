package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.time.LocalDate;
import java.util.Iterator;

public interface DataFeed {
    Iterator<OhlcvBar> open(String symbol, LocalDate from, LocalDate to);
}
