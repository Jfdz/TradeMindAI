package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence port for historical market prices.
 */
public interface StockPriceRepository {

    List<StockPrice> saveAll(List<StockPrice> prices);

    List<StockPrice> findHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to);

    Page<StockPrice> findHistoricalDataPaged(String ticker, TimeFrame timeFrame, LocalDate from, LocalDate to, Pageable pageable);

    Optional<StockPrice> findLatest(Symbol symbol, TimeFrame timeFrame);

    Optional<StockPrice> findLatestByTicker(String ticker, TimeFrame timeFrame);
}
