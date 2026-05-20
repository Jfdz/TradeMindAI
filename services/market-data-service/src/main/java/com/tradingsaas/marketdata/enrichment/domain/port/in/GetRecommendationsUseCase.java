package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import java.util.List;

public interface GetRecommendationsUseCase {

    List<AnalystRecommendation> getRecommendations(String ticker);
}
