package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningsEventResponse(
        String ticker,
        LocalDate period,
        int year,
        int quarter,
        BigDecimal epsActual,
        BigDecimal epsEstimate,
        BigDecimal revenueActual,
        BigDecimal revenueEstimate) {

    public static EarningsEventResponse from(EarningsEvent event) {
        return new EarningsEventResponse(
                event.ticker(),
                event.period(),
                event.year(),
                event.quarter(),
                event.epsActual(),
                event.epsEstimate(),
                event.revenueActual(),
                event.revenueEstimate());
    }
}
