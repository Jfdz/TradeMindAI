package com.tradingsaas.marketdata.domain.service;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import java.util.List;

public interface TechnicalIndicatorCalculator {

    List<TechnicalIndicator> calculateLatestIndicators(Symbol symbol, List<StockPrice> stockPrices);
}
