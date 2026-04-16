package com.tradingsaas.marketdata.domain.port.in;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.util.Optional;

public interface GetLatestPriceUseCase {

    Optional<StockPrice> getLatestPrice(String ticker, TimeFrame timeFrame);
}
