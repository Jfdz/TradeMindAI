package com.tradingsaas.marketdata.enrichment.adapter.out.external;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

class FinnhubAdapterTest {

    private WireMockServer wireMockServer;
    private FinnhubAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        adapter = new FinnhubAdapter(
                WebClient.builder().baseUrl(wireMockServer.baseUrl()).build(),
                "test-api-key",
                new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void fetchProfileParsesCompanyProfileResponse() {
        stubFor(get(urlPathEqualTo("/stock/profile2"))
                .willReturn(okJson("""
                        {
                          "ticker": "AAPL",
                          "name": "Apple Inc",
                          "logo": "https://static2.finnhub.io/AAPL.png",
                          "country": "US",
                          "currency": "USD",
                          "exchange": "NASDAQ",
                          "ipo": "1980-12-12",
                          "marketCapitalization": 2800000.0,
                          "phone": "14089961010",
                          "weburl": "https://www.apple.com/",
                          "finnhubIndustry": "Technology"
                        }
                        """)));

        CompanyProfile profile = adapter.fetchProfile("AAPL");

        assertEquals("AAPL", profile.ticker());
        assertEquals("Apple Inc", profile.name());
        assertEquals("Technology", profile.industry());
        assertNotNull(profile.ipo());
        assertNotNull(profile.marketCap());
    }

    @Test
    void fetchProfileHandlesNullableFields() {
        stubFor(get(urlPathEqualTo("/stock/profile2"))
                .willReturn(okJson("""
                        {"ticker":"BTC-USD","name":"Bitcoin USD"}
                        """)));

        CompanyProfile profile = adapter.fetchProfile("BTC-USD");

        assertEquals("BTC-USD", profile.ticker());
        assertEquals("Bitcoin USD", profile.name());
        assertEquals(null, profile.logo());
        assertEquals(null, profile.ipo());
        assertEquals(null, profile.marketCap());
    }

    @Test
    void fetchProfileThrowsOn429() {
        stubFor(get(urlPathEqualTo("/stock/profile2"))
                .willReturn(aResponse().withStatus(429)));

        assertThrows(EnrichmentUnavailableException.class, () -> adapter.fetchProfile("AAPL"));
    }

    @Test
    void fetchProfileThrowsOn5xx() {
        stubFor(get(urlPathEqualTo("/stock/profile2"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        assertThrows(EnrichmentUnavailableException.class, () -> adapter.fetchProfile("AAPL"));
    }

    @Test
    void fetchMarketNewsReturnsLimitedItems() {
        stubFor(get(urlPathEqualTo("/news"))
                .willReturn(okJson("""
                        [
                          {"id":1,"headline":"Headline 1","datetime":1696779600,"category":"general","source":"Reuters","summary":"S1","url":"https://a.com","image":""},
                          {"id":2,"headline":"Headline 2","datetime":1696779700,"category":"general","source":"Reuters","summary":"S2","url":"https://b.com","image":""},
                          {"id":3,"headline":"Headline 3","datetime":1696779800,"category":"general","source":"Reuters","summary":"S3","url":"https://c.com","image":""}
                        ]
                        """)));

        List<NewsItem> news = adapter.fetchMarketNews("general", 2);

        assertEquals(2, news.size());
        assertEquals(1L, news.get(0).id());
        assertEquals("Headline 1", news.get(0).headline());
        assertEquals(Instant.ofEpochSecond(1696779600), news.get(0).publishedAt());
    }

    @Test
    void fetchTickerNewsReturnsItems() {
        stubFor(get(urlPathEqualTo("/company-news"))
                .willReturn(okJson("""
                        [
                          {"id":10,"headline":"AAPL News","datetime":1696779600,"category":"company","source":"Bloomberg","summary":"S","url":"https://x.com","image":""}
                        ]
                        """)));

        List<NewsItem> news = adapter.fetchTickerNews(
                "AAPL",
                Instant.ofEpochSecond(1696000000),
                Instant.ofEpochSecond(1697000000),
                10);

        assertEquals(1, news.size());
        assertEquals(10L, news.get(0).id());
    }

    @Test
    void fetchEarningsReturnsParsedEvents() {
        stubFor(get(urlPathEqualTo("/stock/earnings"))
                .willReturn(okJson("""
                        [
                          {"symbol":"AAPL","period":"2023-09-30","year":2023,"quarter":4,"actual":1.46,"estimate":1.43}
                        ]
                        """)));

        List<EarningsEvent> events = adapter.fetchEarnings("AAPL");

        assertEquals(1, events.size());
        assertEquals("AAPL", events.get(0).ticker());
        assertEquals(4, events.get(0).quarter());
        assertNotNull(events.get(0).epsActual());
    }

    @Test
    void fetchRecommendationsReturnsParsedData() {
        stubFor(get(urlPathEqualTo("/stock/recommendation"))
                .willReturn(okJson("""
                        [
                          {"symbol":"AAPL","period":"2023-09-01","buy":20,"hold":8,"sell":2,"strongBuy":15,"strongSell":0}
                        ]
                        """)));

        List<AnalystRecommendation> recs = adapter.fetchRecommendations("AAPL");

        assertEquals(1, recs.size());
        assertEquals(20, recs.get(0).buy());
        assertEquals(15, recs.get(0).strongBuy());
    }

    @Test
    void fetchPeersReturnsTickers() {
        stubFor(get(urlPathEqualTo("/stock/peers"))
                .willReturn(okJson("""
                        ["MSFT","GOOGL","META","AMZN"]
                        """)));

        List<String> peers = adapter.fetchPeers("AAPL");

        assertEquals(4, peers.size());
        assertEquals("MSFT", peers.get(0));
    }

    @Test
    void fetchMarketNewsThrowsOn5xx() {
        stubFor(get(urlPathEqualTo("/news"))
                .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        assertThrows(EnrichmentUnavailableException.class, () -> adapter.fetchMarketNews("general", 10));
    }
}
