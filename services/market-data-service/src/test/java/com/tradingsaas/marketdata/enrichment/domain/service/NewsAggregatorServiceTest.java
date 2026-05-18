package com.tradingsaas.marketdata.enrichment.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.config.NewsAggregatorProperties;
import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.NewsProviderPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NewsAggregatorServiceTest {

    private static final Instant FROM = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-05-31T00:00:00Z");

    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final NewsAggregatorProperties props = new NewsAggregatorProperties(5L, Duration.ofMinutes(30));

    @Test
    void returnsEmptyWhenNoProviders() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 10);

        assertEquals(0, result.size());
    }

    @Test
    void mergesItemsFromAllProvidersAndSortsByPublishedDesc() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        NewsItem finnhubA = item(1, "F-headline-A", "https://news.example.com/a", instant("2026-05-12T10:00:00Z"));
        NewsItem yahooB = item(2, "Y-headline-B", "https://news.example.com/b", instant("2026-05-13T10:00:00Z"));
        NewsProviderPort finnhub = providerReturning("finnhub", 10, List.of(finnhubA));
        NewsProviderPort yahoo = providerReturning("yahoo-rss", 20, List.of(yahooB));

        NewsAggregatorService service = new NewsAggregatorService(
                List.of(yahoo, finnhub), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 10);

        assertEquals(2, result.size());
        assertEquals("Y-headline-B", result.get(0).headline(), "newer item should appear first");
        assertEquals("F-headline-A", result.get(1).headline());
    }

    @Test
    void dedupesByUrlWithHigherPriorityWinning() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        String sharedUrl = "https://news.example.com/SHARED/";
        NewsItem finnhubVersion = item(1, "Finnhub version", sharedUrl, instant("2026-05-12T10:00:00Z"));
        NewsItem yahooVersion = item(2, "Yahoo version", sharedUrl.toLowerCase().replace("/shared/", "/shared"),
                instant("2026-05-12T11:00:00Z"));

        NewsProviderPort finnhub = providerReturning("finnhub", 10, List.of(finnhubVersion));
        NewsProviderPort yahoo = providerReturning("yahoo-rss", 20, List.of(yahooVersion));
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(yahoo, finnhub), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 10);

        assertEquals(1, result.size(), "duplicate URL should collapse; got " + result.size());
        assertEquals("Finnhub version", result.get(0).headline(),
                "higher-priority provider (lower number) wins dedupe");
    }

    @Test
    void degradesGracefullyWhenOneProviderThrows() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        NewsItem finnhubA = item(1, "OK headline", "https://news.example.com/a", instant("2026-05-12T10:00:00Z"));
        NewsProviderPort finnhub = providerReturning("finnhub", 10, List.of(finnhubA));
        NewsProviderPort yahoo = mock(NewsProviderPort.class);
        when(yahoo.providerName()).thenReturn("yahoo-rss");
        when(yahoo.priority()).thenReturn(20);
        when(yahoo.fetchTickerNews(anyString(), any(), any(), anyInt()))
                .thenThrow(new EnrichmentUnavailableException("AAPL", "upstream_503", null));

        NewsAggregatorService service = new NewsAggregatorService(
                List.of(yahoo, finnhub), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 10);

        assertEquals(1, result.size());
        assertEquals("OK headline", result.get(0).headline());
    }

    @Test
    void servesFromCacheWhenPresent() {
        NewsItem cached = item(99, "cached", "https://news.example.com/cached", instant("2026-05-12T10:00:00Z"));
        when(cache.get(anyString(), eq(NewsAggregatorService.AggregatedNews.class)))
                .thenReturn(Optional.of(new NewsAggregatorService.AggregatedNews(List.of(cached))));
        NewsProviderPort finnhub = mock(NewsProviderPort.class);
        when(finnhub.providerName()).thenReturn("finnhub");
        when(finnhub.priority()).thenReturn(10);

        NewsAggregatorService service = new NewsAggregatorService(
                List.of(finnhub), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("aapl", FROM, TO, 10);

        assertEquals(1, result.size());
        assertSame(cached, result.get(0));
        verify(finnhub, never()).fetchTickerNews(anyString(), any(), any(), anyInt());
    }

    @Test
    void honorsLimitAfterDedupe() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        List<NewsItem> many = List.of(
                item(1, "a", "https://x/a", instant("2026-05-15T10:00:00Z")),
                item(2, "b", "https://x/b", instant("2026-05-14T10:00:00Z")),
                item(3, "c", "https://x/c", instant("2026-05-13T10:00:00Z")));
        NewsProviderPort finnhub = providerReturning("finnhub", 10, many);
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(finnhub), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 2);

        assertEquals(2, result.size());
        assertTrue(result.get(0).publishedAt().isAfter(result.get(1).publishedAt()));
    }

    @Test
    void cachesResultAfterAggregation() {
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        NewsProviderPort finnhub = providerReturning("finnhub", 10,
                List.of(item(1, "h", "https://x/a", instant("2026-05-12T10:00:00Z"))));
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(finnhub), cache, props, new SimpleMeterRegistry());

        service.aggregateTickerNews("AAPL", FROM, TO, 10);

        verify(cache, times(1)).put(anyString(), any(NewsAggregatorService.AggregatedNews.class),
                eq(Duration.ofMinutes(30)));
    }

    @Test
    void returnsEmptyListWhenLimitIsZero() {
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(mock(NewsProviderPort.class)), cache, props, new SimpleMeterRegistry());

        List<NewsItem> result = service.aggregateTickerNews("AAPL", FROM, TO, 0);

        assertEquals(0, result.size());
    }

    private static NewsProviderPort providerReturning(String name, int priority, List<NewsItem> items) {
        NewsProviderPort p = mock(NewsProviderPort.class);
        when(p.providerName()).thenReturn(name);
        when(p.priority()).thenReturn(priority);
        when(p.fetchTickerNews(anyString(), any(), any(), anyInt())).thenReturn(items);
        return p;
    }

    private static NewsItem item(long id, String headline, String url, Instant publishedAt) {
        return new NewsItem(id, headline, publishedAt, null, "Source", "summary", url, null);
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }
}
