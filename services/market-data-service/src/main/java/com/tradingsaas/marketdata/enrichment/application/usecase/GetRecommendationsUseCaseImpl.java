package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetRecommendationsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetRecommendationsUseCaseImpl implements GetRecommendationsUseCase {

    private static final String KEY_PREFIX = "enrichment:recommendations:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetRecommendationsUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<AnalystRecommendation> getRecommendations(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, List.class).map(raw -> (List<AnalystRecommendation>) raw).orElseGet(() -> {
            List<AnalystRecommendation> recs = provider.fetchRecommendations(ticker);
            cache.put(key, recs, properties.cache().recommendationsTtl());
            return recs;
        });
    }
}
