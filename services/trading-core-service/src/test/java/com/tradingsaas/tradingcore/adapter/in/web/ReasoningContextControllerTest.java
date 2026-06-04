package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.AnalystConsensus;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.RecentPerformance;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.AnalystRecommendationResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.InsufficientHistoryUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import com.tradingsaas.tradingcore.domain.model.RecentTickerPerformance;
import com.tradingsaas.tradingcore.domain.port.out.SignalPerformanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReasoningContextControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");

    private final MarketDataServiceAdapter marketData = mock(MarketDataServiceAdapter.class);
    private final EnrichmentServiceAdapter enrichment = mock(EnrichmentServiceAdapter.class);
    private final SignalPerformanceRepository performance = mock(SignalPerformanceRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReasoningContextController controller =
            new ReasoningContextController(marketData, enrichment, performance, clock);

    @BeforeEach
    void defaultNoHistory() {
        // Default: no resolved track record → null recentPerformance, no error tag.
        // Mockito would otherwise return null and the controller would NPE.
        when(performance.recentPerformanceForTicker(any(), anyInt()))
                .thenReturn(new RecentTickerPerformance(0, 0, 0));
    }

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

    @Test
    void returnsAnalystConsensusFromLatestPeriod() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(enrichment.fetchRecommendations("AAPL")).thenReturn(List.of(
                rec("2026-04-01", 10, 8, 4, 1, 0),
                rec("2026-05-01", 12, 9, 3, 1, 1))); // latest period

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        AnalystConsensus consensus = response.getBody().analystConsensus();
        assertNotNull(consensus);
        assertEquals("2026-05-01", consensus.period());
        assertEquals(12, consensus.strongBuy());
        assertEquals(9, consensus.buy());
        assertEquals(3, consensus.hold());
        assertEquals(1, consensus.sell());
        assertEquals(1, consensus.strongSell());
        assertEquals(26, consensus.total());
        // An empty news result alone tags news_aggregator_empty; analyst data
        // present means no analyst error tag.
        assertFalse(response.getBody().errors().contains("analyst_recs_unavailable"));
    }

    @Test
    void returns200WithAnalystErrorTagWhenRecsThrow() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(sampleNews()));
        when(enrichment.fetchRecommendations("AAPL"))
                .thenThrow(new RuntimeException("recs downstream timeout"));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().analystConsensus());
        assertTrue(response.getBody().errors().contains("analyst_recs_unavailable"));
    }

    @Test
    void returnsInsiderActivityCounts() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(enrichment.fetchInsiderActivity("AAPL"))
                .thenReturn(Optional.of(new EnrichmentServiceAdapter.InsiderActivityResponse("AAPL", 7, 3, 12345L)));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        var insider = response.getBody().insiderActivity();
        assertNotNull(insider);
        assertEquals(7, insider.buyCount());
        assertEquals(3, insider.sellCount());
        assertEquals(12345L, insider.netShares());
        assertFalse(response.getBody().errors().contains("insider_unavailable"));
    }

    @Test
    void insiderActivityIsNullWhenNoTransactions() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(enrichment.fetchInsiderActivity("AAPL"))
                .thenReturn(Optional.of(new EnrichmentServiceAdapter.InsiderActivityResponse("AAPL", 0, 0, 0L)));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertNull(response.getBody().insiderActivity());
        assertFalse(response.getBody().errors().contains("insider_unavailable"));
    }

    @Test
    void returns200WithInsiderErrorTagWhenInsiderThrows() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(sampleNews()));
        when(enrichment.fetchInsiderActivity("AAPL"))
                .thenThrow(new RuntimeException("insider downstream timeout"));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().insiderActivity());
        assertTrue(response.getBody().errors().contains("insider_unavailable"));
    }

    @Test
    void returnsSocialSentimentMentionCounts() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(enrichment.fetchSocialSentiment("AAPL"))
                .thenReturn(Optional.of(
                        new EnrichmentServiceAdapter.SocialSentimentResponse("AAPL", 120, 35, 180)));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        var sentiment = response.getBody().socialSentiment();
        assertNotNull(sentiment);
        assertEquals(120, sentiment.positiveMentions());
        assertEquals(35, sentiment.negativeMentions());
        assertEquals(180, sentiment.totalMentions());
        assertFalse(response.getBody().errors().contains("sentiment_unavailable"));
    }

    @Test
    void returnsRecentPerformanceWinLossCounts() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(performance.recentPerformanceForTicker("AAPL", ReasoningContextController.RECENT_PERFORMANCE_LIMIT))
                .thenReturn(new RecentTickerPerformance(7, 3, 10));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        RecentPerformance rp = response.getBody().recentPerformance();
        assertNotNull(rp);
        assertEquals(7, rp.wins());
        assertEquals(3, rp.losses());
        assertEquals(10, rp.resolvedCount());
        assertFalse(response.getBody().errors().contains("recent_performance_unavailable"));
    }

    @Test
    void recentPerformanceIsNullWhenNoHistory() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        // @BeforeEach already stubs an empty (no-history) record.

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertNull(response.getBody().recentPerformance());
        assertFalse(response.getBody().errors().contains("recent_performance_unavailable"));
    }

    @Test
    void returns200WithRecentPerformanceErrorTagWhenQueryThrows() {
        when(marketData.fetchPriceFacts("AAPL")).thenReturn(Optional.of(sampleFacts()));
        when(enrichment.fetchAggregatedTickerNews(eq("AAPL"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of(sampleNews()));
        when(performance.recentPerformanceForTicker(eq("AAPL"), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        ResponseEntity<ReasoningContextResponse> response =
                controller.getReasoningContext("AAPL", 48, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().recentPerformance());
        assertTrue(response.getBody().errors().contains("recent_performance_unavailable"));
    }

    private static AnalystRecommendationResponse rec(
            String period, int strongBuy, int buy, int hold, int sell, int strongSell) {
        return new AnalystRecommendationResponse(
                "AAPL", LocalDate.parse(period), buy, hold, sell, strongBuy, strongSell);
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
