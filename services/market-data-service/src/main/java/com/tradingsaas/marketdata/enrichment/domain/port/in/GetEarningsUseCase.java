package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import java.util.List;

public interface GetEarningsUseCase {

    List<EarningsEvent> getEarnings(String ticker);
}
