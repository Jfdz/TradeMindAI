package com.tradingsaas.marketdata.enrichment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetSocialSentimentUseCaseImplTest {

    private static final FinnhubProperties PROPS = new FinnhubProperties(
            "https://finnhub.io/api/v1", "test-key", 10,
            new FinnhubProperties.Cache(
                    Duration.ofHours(6), Duration.ofMinutes(30),
                    Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24),
                    Duration.ofHours(12), Duration.ofHours(6)));

    private final MarketEnrichmentProvider provider = mock(MarketEnrichmentProvider.class);
    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final GetSocialSentimentUseCaseImpl useCase =
            new GetSocialSentimentUseCaseImpl(provider, cache, PROPS);

    @Test
    void returnsCachedSentimentWithoutCallingProvider() {
        SocialSentiment cached = new SocialSentiment("AAPL", 100, 20, 130);
        when(cache.get("enrichment:sentiment:AAPL", SocialSentiment.class)).thenReturn(Optional.of(cached));

        assertEquals(cached, useCase.getSocialSentiment("AAPL"));

        verify(provider, never()).fetchSocialSentiment(anyString());
    }

    @Test
    void fetchesAndCachesOnMiss() {
        when(cache.get("enrichment:sentiment:AAPL", SocialSentiment.class)).thenReturn(Optional.empty());
        SocialSentiment fresh = new SocialSentiment("AAPL", 120, 35, 180);
        when(provider.fetchSocialSentiment("AAPL")).thenReturn(fresh);

        assertEquals(fresh, useCase.getSocialSentiment("AAPL"));

        verify(cache).put("enrichment:sentiment:AAPL", fresh, Duration.ofHours(6));
    }
}
