package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.AnalystRecommendationResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.CompanyProfileResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.EarningsEventResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EnrichmentProxyControllerTest {

    private final EnrichmentServiceAdapter adapter = mock(EnrichmentServiceAdapter.class);
    private final EnrichmentProxyController controller = new EnrichmentProxyController(adapter);

    @Test
    void getProfileReturns200WhenFound() {
        CompanyProfileResponse profile = new CompanyProfileResponse(
                "AAPL", "Apple Inc.", null, "US", "USD", "NASDAQ",
                LocalDate.of(1980, 12, 12), new BigDecimal("3000000000000"), null, null, "Technology");
        when(adapter.fetchProfile("AAPL")).thenReturn(Optional.of(profile));

        ResponseEntity<CompanyProfileResponse> response = controller.getProfile("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
    }

    @Test
    void getProfileReturns404WhenNotFound() {
        when(adapter.fetchProfile("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<CompanyProfileResponse> response = controller.getProfile("UNKNOWN");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getMarketNewsReturnsList() {
        NewsItemResponse item = new NewsItemResponse(1L, "Fed cuts rates", "2026-05-01T10:00:00Z",
                "general", "Reuters", "Summary", "http://x.com", null);
        when(adapter.fetchMarketNews("general", 10)).thenReturn(List.of(item));

        ResponseEntity<List<NewsItemResponse>> response = controller.getMarketNews("general", 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Fed cuts rates", response.getBody().getFirst().headline());
    }

    @Test
    void getTickerNewsReturnsList() {
        Instant from = Instant.parse("2026-04-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");
        NewsItemResponse item = new NewsItemResponse(2L, "AAPL beats estimates", "2026-04-25T00:00:00Z",
                "company", "WSJ", "S", "http://x.com", null);
        when(adapter.fetchTickerNews("AAPL", from, to, 20)).thenReturn(List.of(item));

        ResponseEntity<List<NewsItemResponse>> response = controller.getTickerNews(
                "AAPL", "2026-04-01T00:00:00Z", "2026-05-01T00:00:00Z", 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getEarningsReturnsList() {
        EarningsEventResponse event = new EarningsEventResponse(
                "AAPL", LocalDate.of(2026, 3, 31), 2026, 1,
                new BigDecimal("1.52"), new BigDecimal("1.48"), null, null);
        when(adapter.fetchEarnings("AAPL")).thenReturn(List.of(event));

        ResponseEntity<List<EarningsEventResponse>> response = controller.getEarnings("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(new BigDecimal("1.52"), response.getBody().getFirst().epsActual());
    }

    @Test
    void getRecommendationsReturnsList() {
        AnalystRecommendationResponse rec = new AnalystRecommendationResponse(
                "AAPL", LocalDate.of(2026, 5, 1), 20, 5, 2, 10, 1);
        when(adapter.fetchRecommendations("AAPL")).thenReturn(List.of(rec));

        ResponseEntity<List<AnalystRecommendationResponse>> response = controller.getRecommendations("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(20, response.getBody().getFirst().buy());
    }

    @Test
    void getPeersReturnsList() {
        when(adapter.fetchPeers("AAPL")).thenReturn(List.of("MSFT", "GOOGL"));

        ResponseEntity<List<String>> response = controller.getPeers("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("MSFT", "GOOGL"), response.getBody());
    }
}
