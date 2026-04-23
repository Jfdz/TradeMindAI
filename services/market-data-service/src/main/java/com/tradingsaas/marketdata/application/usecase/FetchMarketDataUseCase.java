package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import java.util.List;

public interface FetchMarketDataUseCase {

    List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to);
}
