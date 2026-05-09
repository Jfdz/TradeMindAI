package com.tradingsaas.marketdata.enrichment.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record CompanyProfile(
        String ticker,
        String name,
        String logo,
        String country,
        String currency,
        String exchange,
        LocalDate ipo,
        BigDecimal marketCap,
        String phone,
        String weburl,
        String industry) {

    public CompanyProfile {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
