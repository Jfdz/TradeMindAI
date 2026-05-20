package com.tradingsaas.marketdata.enrichment.domain.port.in;

import java.util.List;

public interface GetPeersUseCase {

    List<String> getPeers(String ticker);
}
