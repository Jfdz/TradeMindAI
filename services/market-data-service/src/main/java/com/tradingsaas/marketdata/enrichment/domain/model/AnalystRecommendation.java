package com.tradingsaas.marketdata.enrichment.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record AnalystRecommendation(
        String ticker,
        LocalDate period,
        int buy,
        int hold,
        int sell,
        int strongBuy,
        int strongSell) {

    public AnalystRecommendation {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(period, "period must not be null");
    }
}
