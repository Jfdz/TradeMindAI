package com.tradingsaas.marketdata.enrichment.domain.port.out;

import java.time.Duration;
import java.util.Optional;

public interface EnrichmentCache {

    <T> Optional<T> get(String key, Class<T> type);

    <T> void put(String key, T value, Duration ttl);

    void evict(String key);
}
