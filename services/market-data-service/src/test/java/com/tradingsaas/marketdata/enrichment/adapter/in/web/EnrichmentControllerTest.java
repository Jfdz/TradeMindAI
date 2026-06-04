package com.tradingsaas.marketdata.enrichment.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.AnalystRecommendationResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.CompanyProfileResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.EarningsEventResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.InsiderActivityResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.MacroContextResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.NewsItemResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.SocialSentimentResponse;
import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;
import com.tradingsaas.marketdata.enrichment.domain.model.MacroContext;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyNewsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyProfileUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetEarningsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetInsiderActivityUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetMacroContextUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetPeersUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetRecommendationsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetSocialSentimentUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EnrichmentControllerTest {

    private final GetCompanyProfileUseCase profileUseCase = mock(GetCompanyProfileUseCase.class);
    private final GetCompanyNewsUseCase newsUseCase = mock(GetCompanyNewsUseCase.class);
    private final GetEarningsUseCase earningsUseCase = mock(GetEarningsUseCase.class);
    private final GetRecommendationsUseCase recommendationsUseCase = mock(GetRecommendationsUseCase.class);
    private final GetPeersUseCase peersUseCase = mock(GetPeersUseCase.class);
    private final GetInsiderActivityUseCase insiderUseCase = mock(GetInsiderActivityUseCase.class);
    private final GetSocialSentimentUseCase sentimentUseCase = mock(GetSocialSentimentUseCase.class);
    private final GetMacroContextUseCase macroUseCase = mock(GetMacroContextUseCase.class);

    private final EnrichmentController controller = new EnrichmentController(
            profileUseCase, newsUseCase, earningsUseCase, recommendationsUseCase, peersUseCase,
            insiderUseCase, sentimentUseCase, macroUseCase);

    @Test
    void getProfileReturnsProfileResponse() {
        CompanyProfile profile = new CompanyProfile("AAPL", "Apple Inc.", null, "US", "USD",
                "NASDAQ", LocalDate.of(1980, 12, 12), new BigDecimal("3000000000000"), null, null, "Technology");
        when(profileUseCase.getProfile("AAPL")).thenReturn(profile);

        ResponseEntity<CompanyProfileResponse> response = controller.getProfile("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
        assertEquals("Apple Inc.", response.getBody().name());
    }

    @Test
    void getMarketNewsReturnsMappedList() {
        NewsItem item = new NewsItem(1L, "Fed raises rates", Instant.parse("2026-05-01T12:00:00Z"),
                "general", "Reuters", "Summary", "https://example.com", null);
        when(newsUseCase.getMarketNews("general", 10)).thenReturn(List.of(item));

        ResponseEntity<List<NewsItemResponse>> response = controller.getMarketNews("general", 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Fed raises rates", response.getBody().getFirst().headline());
    }

    @Test
    void getTickerNewsReturnsMappedList() {
        Instant from = Instant.parse("2026-04-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-01T00:00:00Z");
        NewsItem item = new NewsItem(2L, "AAPL earnings beat", from,
                "company news", "Bloomberg", "Summary", "https://example.com", null);
        when(newsUseCase.getTickerNews("AAPL", from, to, 20)).thenReturn(List.of(item));

        ResponseEntity<List<NewsItemResponse>> response = controller.getTickerNews(
                "AAPL", "2026-04-01T00:00:00Z", "2026-05-01T00:00:00Z", 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("AAPL earnings beat", response.getBody().getFirst().headline());
    }

    @Test
    void getEarningsReturnsMappedList() {
        EarningsEvent event = new EarningsEvent("AAPL", LocalDate.of(2026, 3, 31), 2026, 1,
                new BigDecimal("1.52"), new BigDecimal("1.48"), null, null);
        when(earningsUseCase.getEarnings("AAPL")).thenReturn(List.of(event));

        ResponseEntity<List<EarningsEventResponse>> response = controller.getEarnings("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(new BigDecimal("1.52"), response.getBody().getFirst().epsActual());
    }

    @Test
    void getRecommendationsReturnsMappedList() {
        AnalystRecommendation rec = new AnalystRecommendation("AAPL", LocalDate.of(2026, 5, 1), 20, 5, 2, 10, 1);
        when(recommendationsUseCase.getRecommendations("AAPL")).thenReturn(List.of(rec));

        ResponseEntity<List<AnalystRecommendationResponse>> response = controller.getRecommendations("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(20, response.getBody().getFirst().buy());
    }

    @Test
    void getPeersReturnsList() {
        when(peersUseCase.getPeers("AAPL")).thenReturn(List.of("MSFT", "GOOGL", "META"));

        ResponseEntity<List<String>> response = controller.getPeers("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(List.of("MSFT", "GOOGL", "META"), response.getBody());
    }

    @Test
    void getInsiderReturnsActivityResponse() {
        when(insiderUseCase.getInsiderActivity("AAPL"))
                .thenReturn(new InsiderActivity("AAPL", 7, 3, 12345L));

        ResponseEntity<InsiderActivityResponse> response = controller.getInsider("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
        assertEquals(7, response.getBody().buyCount());
        assertEquals(3, response.getBody().sellCount());
        assertEquals(12345L, response.getBody().netShares());
    }

    @Test
    void getSentimentReturnsMentionCounts() {
        when(sentimentUseCase.getSocialSentiment("AAPL"))
                .thenReturn(new SocialSentiment("AAPL", 120, 35, 180));

        ResponseEntity<SocialSentimentResponse> response = controller.getSentiment("AAPL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
        assertEquals(120, response.getBody().positiveMentions());
        assertEquals(35, response.getBody().negativeMentions());
        assertEquals(180, response.getBody().totalMentions());
    }

    @Test
    void getMacroReturnsRecentNewsCount() {
        when(macroUseCase.getMacroContext()).thenReturn(new MacroContext(42));

        ResponseEntity<MacroContextResponse> response = controller.getMacro();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(42, response.getBody().recentMarketNewsCount());
    }
}
