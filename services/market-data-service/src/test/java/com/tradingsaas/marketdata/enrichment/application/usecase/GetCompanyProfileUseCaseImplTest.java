package com.tradingsaas.marketdata.enrichment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetCompanyProfileUseCaseImplTest {

    private static final FinnhubProperties PROPS = new FinnhubProperties(
            "https://finnhub.io/api/v1", "test-key", 10,
            new FinnhubProperties.Cache(
                    Duration.ofHours(6), Duration.ofMinutes(30),
                    Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(24)));

    private final MarketEnrichmentProvider provider = mock(MarketEnrichmentProvider.class);
    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final GetCompanyProfileUseCaseImpl useCase =
            new GetCompanyProfileUseCaseImpl(provider, cache, PROPS);

    @Test
    void returnsCachedProfileWithoutCallingProvider() {
        CompanyProfile cached = profile("AAPL");
        when(cache.get("enrichment:profile:AAPL", CompanyProfile.class)).thenReturn(Optional.of(cached));

        CompanyProfile result = useCase.getProfile("AAPL");

        assertEquals(cached, result);
        verify(provider, never()).fetchProfile(any());
    }

    @Test
    void fetchesProfileFromProviderAndCachesOnMiss() {
        CompanyProfile fromProvider = profile("AAPL");
        when(cache.get("enrichment:profile:AAPL", CompanyProfile.class)).thenReturn(Optional.empty());
        when(provider.fetchProfile("AAPL")).thenReturn(fromProvider);

        CompanyProfile result = useCase.getProfile("AAPL");

        assertEquals(fromProvider, result);
        verify(provider).fetchProfile("AAPL");
        verify(cache).put(eq("enrichment:profile:AAPL"), eq(fromProvider), eq(Duration.ofHours(6)));
    }

    @Test
    void normalizesTickerToUppercaseForCacheKey() {
        CompanyProfile fromProvider = profile("AAPL");
        when(cache.get("enrichment:profile:AAPL", CompanyProfile.class)).thenReturn(Optional.empty());
        when(provider.fetchProfile("aapl")).thenReturn(fromProvider);

        useCase.getProfile("aapl");

        verify(cache).get("enrichment:profile:AAPL", CompanyProfile.class);
        verify(cache).put(eq("enrichment:profile:AAPL"), any(), any());
    }

    private static CompanyProfile profile(String ticker) {
        return new CompanyProfile(ticker, "Apple Inc.", null, "US", "USD",
                "NASDAQ", LocalDate.of(1980, 12, 12), new BigDecimal("3000000000000"), null, null, "Technology");
    }
}
