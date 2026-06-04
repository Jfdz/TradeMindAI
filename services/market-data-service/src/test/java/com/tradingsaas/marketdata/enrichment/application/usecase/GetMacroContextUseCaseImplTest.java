package com.tradingsaas.marketdata.enrichment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.MacroContext;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetMacroContextUseCaseImplTest {

    private static final FinnhubProperties PROPS = new FinnhubProperties(
            "https://finnhub.io/api/v1", "test-key", 10,
            new FinnhubProperties.Cache(
                    Duration.ofHours(6), Duration.ofMinutes(30),
                    Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24),
                    Duration.ofHours(12), Duration.ofHours(6), Duration.ofHours(1)));

    private final MarketEnrichmentProvider provider = mock(MarketEnrichmentProvider.class);
    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final GetMacroContextUseCaseImpl useCase =
            new GetMacroContextUseCaseImpl(provider, cache, PROPS);

    @Test
    void returnsCachedMacroWithoutCallingProvider() {
        MacroContext cached = new MacroContext(10);
        when(cache.get("enrichment:macro:global", MacroContext.class)).thenReturn(Optional.of(cached));

        assertEquals(cached, useCase.getMacroContext());

        verify(provider, never()).fetchMacroContext();
    }

    @Test
    void fetchesAndCachesOnMiss() {
        when(cache.get("enrichment:macro:global", MacroContext.class)).thenReturn(Optional.empty());
        MacroContext fresh = new MacroContext(42);
        when(provider.fetchMacroContext()).thenReturn(fresh);

        assertEquals(fresh, useCase.getMacroContext());

        verify(cache).put("enrichment:macro:global", fresh, Duration.ofHours(1));
    }
}
