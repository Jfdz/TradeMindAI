package com.tradingsaas.marketdata.enrichment.domain.service;

import com.tradingsaas.marketdata.enrichment.config.NewsAggregatorProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.NewsProviderPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fans out ticker-news requests to every registered {@link NewsProviderPort}
 * in parallel, dedupes by normalized URL (lower-priority wins),
 * sorts by {@code publishedAt} descending, and caches the result.
 *
 * <p>Graceful degradation: a single provider failure does not fail the
 * aggregate response. The aggregator returns whatever succeeded. If all
 * providers fail, returns an empty list — never throws.
 */
@Service
public class NewsAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(NewsAggregatorService.class);
    private static final String CACHE_KEY_PREFIX = "enrichment:news-aggregated:";

    private final List<NewsProviderPort> providers;
    private final EnrichmentCache cache;
    private final NewsAggregatorProperties properties;
    private final MeterRegistry meterRegistry;

    public NewsAggregatorService(
            List<NewsProviderPort> providers,
            EnrichmentCache cache,
            NewsAggregatorProperties properties,
            MeterRegistry meterRegistry) {
        this.providers = Objects.requireNonNull(providers, "providers must not be null").stream()
                .sorted(Comparator.comparingInt(NewsProviderPort::priority))
                .toList();
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.meterRegistry = meterRegistry;
        log.info(
                "event=news_aggregator.initialized providers={}",
                this.providers.stream().map(NewsProviderPort::providerName).toList());
    }

    public List<NewsItem> aggregateTickerNews(String ticker, Instant from, Instant to, int limit) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (limit <= 0) {
            return List.of();
        }
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        String cacheKey = CACHE_KEY_PREFIX + normalizedTicker
                + ":" + from.getEpochSecond() + ":" + to.getEpochSecond() + ":" + limit;

        Optional<AggregatedNews> cached = cache.get(cacheKey, AggregatedNews.class);
        if (cached.isPresent()) {
            return cached.get().items();
        }

        if (providers.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ProviderResult>> futures = providers.stream()
                .map(p -> fetchAsync(p, normalizedTicker, from, to, limit))
                .toList();
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            all.get(properties.parallelTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("event=news_aggregator.timeout ticker={} timeoutSeconds={}",
                    normalizedTicker, properties.parallelTimeoutSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("event=news_aggregator.interrupted ticker={}", normalizedTicker);
        } catch (ExecutionException e) {
            // individual provider failures already logged inside fetchAsync
        }

        List<NewsItem> merged = mergeAndDedupe(futures, limit);
        cache.put(cacheKey, new AggregatedNews(merged), properties.cacheTtl());
        recordOutcome("ok");
        return merged;
    }

    private CompletableFuture<ProviderResult> fetchAsync(
            NewsProviderPort provider, String ticker, Instant from, Instant to, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<NewsItem> items = provider.fetchTickerNews(ticker, from, to, limit);
                recordProvider(provider.providerName(), "ok");
                return new ProviderResult(provider, items);
            } catch (RuntimeException e) {
                recordProvider(provider.providerName(), "error");
                log.warn(
                        "event=news_aggregator.provider_failed ticker={} provider={} message={}",
                        ticker,
                        provider.providerName(),
                        e.getMessage());
                return new ProviderResult(provider, List.of());
            }
        });
    }

    private List<NewsItem> mergeAndDedupe(List<CompletableFuture<ProviderResult>> futures, int limit) {
        // Lower priority value wins ties — we already sorted providers by priority,
        // so iterate in that order and keep the first occurrence per normalized URL.
        Map<String, NewsItem> byUrl = new LinkedHashMap<>();
        List<NewsItem> noUrl = new ArrayList<>();
        for (CompletableFuture<ProviderResult> f : futures) {
            ProviderResult result = f.getNow(new ProviderResult(null, List.of()));
            for (NewsItem item : result.items()) {
                String key = normalizeUrl(item.url());
                if (key == null) {
                    noUrl.add(item);
                } else if (!byUrl.containsKey(key)) {
                    byUrl.put(key, item);
                }
            }
        }
        List<NewsItem> all = new ArrayList<>(byUrl.size() + noUrl.size());
        all.addAll(byUrl.values());
        all.addAll(noUrl);
        all.sort(Comparator.comparing(NewsItem::publishedAt, Comparator.reverseOrder()));
        return all.size() > limit ? all.subList(0, limit) : List.copyOf(all);
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void recordProvider(String providerName, String outcome) {
        Counter.builder("news_aggregator_provider_total")
                .tag("provider", providerName)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void recordOutcome(String outcome) {
        Counter.builder("news_aggregator_total")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private record ProviderResult(NewsProviderPort provider, List<NewsItem> items) {}

    /** Cache wrapper so Jackson can deserialize back into a typed object. */
    public record AggregatedNews(List<NewsItem> items) {}
}
