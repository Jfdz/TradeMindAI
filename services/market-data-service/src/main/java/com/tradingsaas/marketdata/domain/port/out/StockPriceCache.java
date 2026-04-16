package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.util.Optional;

/**
 * Cache port for latest stock prices (Redis, TTL 5 min).
 */
public interface StockPriceCache {

    void cacheLatest(StockPrice price);

    Optional<StockPrice> findLatest(String ticker, TimeFrame timeFrame);

    void evict(String ticker, TimeFrame timeFrame);
}
