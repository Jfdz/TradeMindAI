package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import java.util.List;

/**
 * External market data source.
 */
public interface MarketDataProvider {

    List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to);
}
