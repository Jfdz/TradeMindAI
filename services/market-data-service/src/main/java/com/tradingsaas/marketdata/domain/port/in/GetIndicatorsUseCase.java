package com.tradingsaas.marketdata.domain.port.in;

import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.util.List;

public interface GetIndicatorsUseCase {

    List<TechnicalIndicator> getLatestIndicators(String ticker, List<TechnicalIndicatorType> types);
}
