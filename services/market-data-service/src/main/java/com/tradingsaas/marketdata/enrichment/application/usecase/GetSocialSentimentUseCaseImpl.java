package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetSocialSentimentUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetSocialSentimentUseCaseImpl implements GetSocialSentimentUseCase {

    private static final String KEY_PREFIX = "enrichment:sentiment:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetSocialSentimentUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public SocialSentiment getSocialSentiment(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, SocialSentiment.class).orElseGet(() -> {
            SocialSentiment sentiment = provider.fetchSocialSentiment(ticker);
            cache.put(key, sentiment, properties.cache().sentimentTtl());
            return sentiment;
        });
    }
}
