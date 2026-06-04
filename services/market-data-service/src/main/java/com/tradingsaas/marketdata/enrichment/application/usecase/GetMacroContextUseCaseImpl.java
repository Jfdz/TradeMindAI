package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.MacroContext;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetMacroContextUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetMacroContextUseCaseImpl implements GetMacroContextUseCase {

    // Market-wide, not per-ticker: a single global cache key.
    private static final String KEY = "enrichment:macro:global";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetMacroContextUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public MacroContext getMacroContext() {
        return cache.get(KEY, MacroContext.class).orElseGet(() -> {
            MacroContext macro = provider.fetchMacroContext();
            cache.put(KEY, macro, properties.cache().macroTtl());
            return macro;
        });
    }
}
