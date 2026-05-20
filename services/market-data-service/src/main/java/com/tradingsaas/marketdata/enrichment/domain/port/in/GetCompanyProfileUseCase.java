package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;

public interface GetCompanyProfileUseCase {

    CompanyProfile getProfile(String ticker);
}
