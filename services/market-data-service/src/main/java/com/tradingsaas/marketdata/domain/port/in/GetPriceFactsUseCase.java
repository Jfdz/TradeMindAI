package com.tradingsaas.marketdata.domain.port.in;

import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.util.Optional;

/**
 * Input port: build a deterministic price + indicator snapshot for a ticker.
 *
 * <p>Returns {@link Optional#empty()} when the ticker is not tracked or
 * has zero history. Throws {@code InsufficientHistoryException} when the
 * lookback window is too short to compute the full set of indicators.
 */
public interface GetPriceFactsUseCase {

    Optional<PriceFacts> getPriceFacts(String ticker, TimeFrame timeFrame);
}
