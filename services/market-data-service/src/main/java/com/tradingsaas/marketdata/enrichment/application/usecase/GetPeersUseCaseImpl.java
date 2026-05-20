package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetPeersUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetPeersUseCaseImpl implements GetPeersUseCase {

    private static final String KEY_PREFIX = "enrichment:peers:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetPeersUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<String> getPeers(String ticker) {
        String key = KEY_PREFIX + ticker.toUpperCase();
        return cache.get(key, List.class).map(raw -> (List<String>) raw).orElseGet(() -> {
            List<String> peers = provider.fetchPeers(ticker);
            cache.put(key, peers, properties.cache().peersTtl());
            return peers;
        });
    }
}
