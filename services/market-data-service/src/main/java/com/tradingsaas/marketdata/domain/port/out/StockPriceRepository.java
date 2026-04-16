package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for historical market prices.
 */
public interface StockPriceRepository {

    List<StockPrice> saveAll(List<StockPrice> prices);

    List<StockPrice> findHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to);

    Optional<StockPrice> findLatest(Symbol symbol, TimeFrame timeFrame);
}
