package com.tradingsaas.marketdata.enrichment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record EarningsEvent(
        String ticker,
        LocalDate period,
        int year,
        int quarter,
        BigDecimal epsActual,
        BigDecimal epsEstimate,
        BigDecimal revenueActual,
        BigDecimal revenueEstimate) {

    public EarningsEvent {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(period, "period must not be null");
    }
}
