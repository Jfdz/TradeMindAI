package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CompanyProfileResponse(
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

    public static CompanyProfileResponse from(CompanyProfile profile) {
        return new CompanyProfileResponse(
                profile.ticker(),
                profile.name(),
                profile.logo(),
                profile.country(),
                profile.currency(),
                profile.exchange(),
                profile.ipo(),
                profile.marketCap(),
                profile.phone(),
                profile.weburl(),
                profile.industry());
    }
}
