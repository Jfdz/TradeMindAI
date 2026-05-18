package com.tradingsaas.marketdata.enrichment.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.NewsItemResponse;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.service.NewsAggregatorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NewsAggregatorControllerTest {

    private final NewsAggregatorService aggregator = mock(NewsAggregatorService.class);
    private final NewsAggregatorController controller = new NewsAggregatorController(aggregator);

    @Test
    void returnsAggregatedItemsWithIsoTimestamps() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-31T00:00:00Z");
        NewsItem item = new NewsItem(
                1L, "Headline", Instant.parse("2026-05-12T10:00:00Z"),
                "category", "Source", "summary", "https://news/x", "https://news/x.png");
        when(aggregator.aggregateTickerNews(eq("AAPL"), eq(from), eq(to), eq(20)))
                .thenReturn(List.of(item));

        ResponseEntity<List<NewsItemResponse>> response = controller.getAggregatedTickerNews(
                "AAPL", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Headline", response.getBody().get(0).headline());
        assertEquals("2026-05-12T10:00:00Z", response.getBody().get(0).publishedAt());
    }

    @Test
    void returns400ForMalformedIsoTimestamps() {
        ResponseEntity<List<NewsItemResponse>> response = controller.getAggregatedTickerNews(
                "AAPL", "yesterday", "tomorrow", 20);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returns400WhenFromAfterTo() {
        ResponseEntity<List<NewsItemResponse>> response = controller.getAggregatedTickerNews(
                "AAPL", "2026-06-01T00:00:00Z", "2026-05-01T00:00:00Z", 20);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void clampsLimitWithinAllowedRange() {
        when(aggregator.aggregateTickerNews(eq("AAPL"), eq(Instant.parse("2026-05-01T00:00:00Z")),
                eq(Instant.parse("2026-05-31T00:00:00Z")), anyInt())).thenReturn(List.of());

        controller.getAggregatedTickerNews(
                "AAPL", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 9999);
        verify(aggregator).aggregateTickerNews(
                eq("AAPL"), eq(Instant.parse("2026-05-01T00:00:00Z")),
                eq(Instant.parse("2026-05-31T00:00:00Z")), eq(NewsAggregatorController.MAX_LIMIT));

        controller.getAggregatedTickerNews(
                "AAPL", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 0);
        verify(aggregator).aggregateTickerNews(
                eq("AAPL"), eq(Instant.parse("2026-05-01T00:00:00Z")),
                eq(Instant.parse("2026-05-31T00:00:00Z")), eq(1));
    }

    @Test
    void returnsEmptyListWhenAggregatorReturnsNothing() {
        when(aggregator.aggregateTickerNews(eq("UNKNOWN"), eq(Instant.parse("2026-05-01T00:00:00Z")),
                eq(Instant.parse("2026-05-31T00:00:00Z")), eq(20))).thenReturn(List.of());

        ResponseEntity<List<NewsItemResponse>> response = controller.getAggregatedTickerNews(
                "UNKNOWN", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
    }
}
