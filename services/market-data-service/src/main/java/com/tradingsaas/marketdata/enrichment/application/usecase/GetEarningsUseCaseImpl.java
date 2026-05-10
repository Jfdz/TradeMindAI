package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetEarningsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetEarningsUseCaseImpl implements GetEarningsUseCase {

    private static final String KEY_PREFIX = "enrichment:earnings:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetEarningsUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<EarningsEvent> getEarnings(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, List.class).map(raw -> (List<EarningsEvent>) raw).orElseGet(() -> {
            List<EarningsEvent> events = provider.fetchEarnings(ticker);
            cache.put(key, events, properties.cache().earningsTtl());
            return events;
        });
    }
}
