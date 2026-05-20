package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.InsufficientHistoryUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReasoningContextControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");

    private final MarketDataServiceAdapter marketData = mock(MarketDataServiceAdapter.class);
    private final EnrichmentServiceAdapter enrichment = mock(EnrichmentServiceAdapter.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReasoningContextController controller =
            new ReasoningContextController(marketData, enrichment, clock);

    @Test
    void returnsCombinedContextOn200() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        // C1.5 — controller clamps limit to MAX_NEWS_LIMIT (now 4). The
        // request still passes 8; the stub must match the clamped value.
        when(enrichment.fetchAggregatedTickerNews(
                        eq("AAPL"), any(Instant.class), eq(NOW),
                        eq(ReasoningContextController.MAX_NEWS_LIMIT)))
                .thenReturn(List.of(sampleNews()));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
        assertEquals(NOW, response.getBody().generatedAt());
        assertEquals(new BigDecimal("173.45"), response.getBody().priceFacts().close());
        assertEquals(1, response.getBody().news().size());
        assertEquals(0, response.getBody().errors().size());
    }

    @Test
    void returns404WhenPriceFactsMissing() {
        when(marketData.fetchPriceFacts("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("UNKNOWN", 48, 8);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returns200WithErrorTagWhenNewsAggregatorThrows() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("downstream timeout"));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().news().size());
        assertTrue(response.getBody().errors().contains("news_aggregator_unavailable"),
                "expected errors to contain news_aggregator_unavailable; got " + response.getBody().errors());
    }

    @Test
    void returns200WithEmptyErrorTagWhenNewsIsEmpty() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().errors().contains("news_aggregator_empty"));
    }

    @Test
    void returns422OnInsufficientHistoryFromMarketData() {
        when(marketData.fetchPriceFacts("NEWCO"))
                .thenThrow(new InsufficientHistoryUpstreamException("NEWCO"));

        InsufficientHistoryUpstreamException ex = new InsufficientHistoryUpstreamException("NEWCO");
        ResponseEntity<Map<String, Object>> response = controller.handleInsufficientHistory(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("INSUFFICIENT_HISTORY", response.getBody().get("error"));
        assertEquals("NEWCO", response.getBody().get("ticker"));
    }

    @Test
    void returns503WhenMarketDataUpstreamFails() {
        MarketDataUpstreamException ex = new MarketDataUpstreamException("market-data unavailable for AAPL");
        ResponseEntity<Map<String, Object>> response = controller.handleUpstreamFailure(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("MARKET_DATA_UNAVAILABLE", response.getBody().get("error"));
    }

    @Test
    void clampsNewsHoursAndLimitWithinAllowedRange() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), eq(NOW), eq(ReasoningContextController.MAX_NEWS_LIMIT)))
                .thenReturn(List.of(sampleNews()));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 999_999, 9999);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void upperCasesTickerInResponse() {
        when(marketData.fetchPriceFacts("aapl")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("aapl"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("aapl", 48, 8);

        assertEquals("AAPL", response.getBody().ticker());
    }

    private static PriceFactsResponse sampleFacts() {
        return new PriceFactsResponse(
                "AAPL", "DAILY", "2026-05-12", 252,
                new BigDecimal("173.45"), new BigDecimal("170.10"),
                new BigDecimal("1.97"), null, null,
                new BigDecimal("180.00"), new BigDecimal("150.00"),
                new BigDecimal("172.00"), new BigDecimal("165.00"), new BigDecimal("158.40"),
                new BigDecimal("58.3"), new BigDecimal("0.50"),
                12_400_000L, new BigDecimal("11500000.00"),
                new BigDecimal("168.00"), new BigDecimal("178.00"));
    }

    private static NewsItemResponse sampleNews() {
        return new NewsItemResponse(
                42L, "AAPL: strong Q1", "2026-05-12T10:00:00Z",
                null, "Reuters", "summary", "https://x/a", "https://x/a.png");
    }
}
