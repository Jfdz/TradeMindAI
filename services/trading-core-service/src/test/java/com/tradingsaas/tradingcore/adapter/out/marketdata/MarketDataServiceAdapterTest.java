package com.tradingsaas.tradingcore.adapter.out.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.LatestPricesResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataPage;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketPriceResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class MarketDataServiceAdapterTest {

    @Test
    void sendsInternalSecretOnLatestPriceProxyRequest() {
        MarketDataServiceAdapter adapter = createAdapter(request -> {
            assertEquals(HttpMethod.GET, request.method());
            assertEquals("/api/v1/prices/AAPL/latest", request.url().getPath());
            assertEquals("DAILY", request.url().getQuery().split("=")[1]);
            assertEquals("secret-123", request.headers().getFirst("X-Internal-Secret"));
            return json("""
                    {
                      "ticker": "AAPL",
                      "date": "2026-04-29",
                      "timeFrame": "DAILY",
                      "ohlcv": {"open": 170.0, "high": 175.0, "low": 169.0, "close": 174.0, "volume": 1000},
                      "adjustedClose": 174.0
                    }
                    """);
        });

        MarketPriceResponse response = adapter.fetchLatestPrice("AAPL", "DAILY").orElseThrow();

        assertEquals("AAPL", response.ticker());
        assertEquals(174.0, response.ohlcv().close());
    }

    @Test
    void mapsEmptyLatestPricesWhenNoTickersProvided() {
        MarketDataServiceAdapter adapter = createAdapter(request -> {
            throw new AssertionError("No HTTP request expected");
        });

        LatestPricesResponse response = adapter.fetchLatestPrices(List.of(), "DAILY");

        assertTrue(response.prices().isEmpty());
    }

    @Test
    void sendsInternalSecretOnHistoricalProxyRequest() {
        MarketDataServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/prices/MSFT/history", request.url().getPath());
            assertTrue(request.url().getQuery().contains("size=25"));
            assertEquals("secret-123", request.headers().getFirst("X-Internal-Secret"));
            return json("""
                    {
                      "content": [],
                      "page": 0,
                      "size": 25,
                      "totalElements": 0,
                      "totalPages": 0
                    }
                    """);
        });

        MarketDataPage<MarketPriceResponse> response = adapter.fetchHistoricalPrices(
                "MSFT", "DAILY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 29), 0, 25);

        assertEquals(25, response.size());
        assertTrue(response.content().isEmpty());
    }

    private MarketDataServiceAdapter createAdapter(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        return new MarketDataServiceAdapter(webClient, "secret-123");
    }

    private static Mono<ClientResponse> json(String body) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
