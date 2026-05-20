package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyProfileUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetCompanyProfileUseCaseImpl implements GetCompanyProfileUseCase {

    private static final String KEY_PREFIX = "enrichment:profile:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetCompanyProfileUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public CompanyProfile getProfile(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, CompanyProfile.class).orElseGet(() -> {
            CompanyProfile profile = provider.fetchProfile(ticker);
            cache.put(key, profile, properties.cache().profileTtl());
            return profile;
        });
    }
}
