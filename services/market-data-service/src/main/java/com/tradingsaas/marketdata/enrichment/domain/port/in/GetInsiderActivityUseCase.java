package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;

public interface GetInsiderActivityUseCase {

    InsiderActivity getInsiderActivity(String ticker);
}
