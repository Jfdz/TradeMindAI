package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.model.Symbol;
import java.time.LocalDate;

/**
 * Publishes market data domain events.
 */
public interface MarketDataEventPublisher {

    void publishPricesUpdated(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to, int count);
}
