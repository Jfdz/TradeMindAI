package com.tradingsaas.tradingcore.adapter.out.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.CompanyProfileResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class EnrichmentServiceAdapterTest {

    @Test
    void fetchProfileSendsInternalSecretAndMapsResponse() {
        EnrichmentServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/enrichment/profile/AAPL", request.url().getPath());
            assertEquals("secret-x", request.headers().getFirst("X-Internal-Secret"));
            return json("""
                    {
                      "ticker": "AAPL",
                      "name": "Apple Inc.",
                      "country": "US",
                      "currency": "USD",
                      "exchange": "NASDAQ"
                    }
                    """);
        });

        Optional<CompanyProfileResponse> result = adapter.fetchProfile("AAPL");

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().ticker());
        assertEquals("Apple Inc.", result.get().name());
    }

    @Test
    void fetchProfileReturnsEmptyOn4xx() {
        EnrichmentServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        Optional<CompanyProfileResponse> result = adapter.fetchProfile("UNKNOWN");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchProfileReturnsEmptyOn5xx() {
        EnrichmentServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        Optional<CompanyProfileResponse> result = adapter.fetchProfile("AAPL");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchMarketNewsLimitAndCategoryPassedAsQueryParams() {
        EnrichmentServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/enrichment/news", request.url().getPath());
            assertTrue(request.url().getQuery().contains("category=crypto"));
            assertTrue(request.url().getQuery().contains("limit=5"));
            assertEquals("secret-x", request.headers().getFirst("X-Internal-Secret"));
            return json("""
                    [
                      {"id": 1, "headline": "BTC hits ATH", "publishedAt": "2026-05-01T10:00:00Z",
                       "category": "crypto", "source": "CoinDesk", "summary": "S", "url": "http://x.com", "image": null}
                    ]
                    """);
        });

        List<NewsItemResponse> result = adapter.fetchMarketNews("crypto", 5);

        assertFalse(result.isEmpty());
        assertEquals("BTC hits ATH", result.getFirst().headline());
    }

    @Test
    void fetchMarketNewsReturnsEmptyListOn5xx() {
        EnrichmentServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        List<NewsItemResponse> result = adapter.fetchMarketNews("general", 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchTickerNewsPassesFromToLimitParams() {
        EnrichmentServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/enrichment/news/AAPL", request.url().getPath());
            String query = request.url().getQuery();
            assertTrue(query.contains("from=2026-04-01T00%3A00%3A00Z") || query.contains("from=2026-04-01T00:00:00Z"));
            return json("""
                    [
                      {"id": 2, "headline": "AAPL earnings beat", "publishedAt": "2026-04-25T12:00:00Z",
                       "category": "company", "source": "WSJ", "summary": "S", "url": "http://x.com", "image": null}
                    ]
                    """);
        });

        List<NewsItemResponse> result = adapter.fetchTickerNews(
                "AAPL", Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"), 10);

        assertEquals(1, result.size());
        assertEquals("AAPL earnings beat", result.getFirst().headline());
    }

    @Test
    void fetchPeersReturnsList() {
        EnrichmentServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/enrichment/peers/AAPL", request.url().getPath());
            return json("""
                    ["MSFT", "GOOGL", "META"]
                    """);
        });

        List<String> result = adapter.fetchPeers("AAPL");

        assertEquals(List.of("MSFT", "GOOGL", "META"), result);
    }

    private EnrichmentServiceAdapter createAdapter(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        return new EnrichmentServiceAdapter(webClient, "secret-x");
    }

    private static Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
