package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetInsiderActivityUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetInsiderActivityUseCaseImpl implements GetInsiderActivityUseCase {

    private static final String KEY_PREFIX = "enrichment:insider:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetInsiderActivityUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public InsiderActivity getInsiderActivity(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, InsiderActivity.class).orElseGet(() -> {
            InsiderActivity activity = provider.fetchInsiderActivity(ticker);
            cache.put(key, activity, properties.cache().insiderTtl());
            return activity;
        });
    }
}
