package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import java.time.LocalDate;

public record AnalystRecommendationResponse(
        String ticker,
        LocalDate period,
        int buy,
        int hold,
        int sell,
        int strongBuy,
        int strongSell) {

    public static AnalystRecommendationResponse from(AnalystRecommendation rec) {
        return new AnalystRecommendationResponse(
                rec.ticker(),
                rec.period(),
                rec.buy(),
                rec.hold(),
                rec.sell(),
                rec.strongBuy(),
                rec.strongSell());
    }
}
