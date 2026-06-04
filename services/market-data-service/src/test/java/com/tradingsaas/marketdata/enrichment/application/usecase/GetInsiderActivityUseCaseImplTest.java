package com.tradingsaas.marketdata.enrichment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetInsiderActivityUseCaseImplTest {

    private static final FinnhubProperties PROPS = new FinnhubProperties(
            "https://finnhub.io/api/v1", "test-key", 10,
            new FinnhubProperties.Cache(
                    Duration.ofHours(6), Duration.ofMinutes(30),
                    Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24),
                    Duration.ofHours(12)));

    private final MarketEnrichmentProvider provider = mock(MarketEnrichmentProvider.class);
    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final GetInsiderActivityUseCaseImpl useCase =
            new GetInsiderActivityUseCaseImpl(provider, cache, PROPS);

    @Test
    void returnsCachedActivityWithoutCallingProvider() {
        InsiderActivity cached = new InsiderActivity("AAPL", 5, 2, 100L);
        when(cache.get("enrichment:insider:AAPL", InsiderActivity.class)).thenReturn(Optional.of(cached));

        assertEquals(cached, useCase.getInsiderActivity("AAPL"));

        verify(provider, never()).fetchInsiderActivity(anyString());
    }

    @Test
    void fetchesAndCachesOnMiss() {
        when(cache.get("enrichment:insider:AAPL", InsiderActivity.class)).thenReturn(Optional.empty());
        InsiderActivity fresh = new InsiderActivity("AAPL", 7, 3, 12345L);
        when(provider.fetchInsiderActivity("AAPL")).thenReturn(fresh);

        assertEquals(fresh, useCase.getInsiderActivity("AAPL"));

        verify(cache).put("enrichment:insider:AAPL", fresh, Duration.ofHours(12));
    }
}
