package com.tradingsaas.tradingcore.adapter.out.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.InsufficientHistoryUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class MarketDataServiceAdapterPriceFactsTest {

    @Test
    void fetchPriceFactsReturnsResponseOn200() {
        MarketDataServiceAdapter adapter = createAdapter(request -> {
            assertEquals("/api/v1/price-facts/AAPL", request.url().getPath());
            assertEquals("secret-x", request.headers().getFirst("X-Internal-Secret"));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "ticker": "AAPL",
                              "timeframe": "DAILY",
                              "snapshotAt": "2026-05-12",
                              "barsAvailable": 252,
                              "close": 173.45,
                              "previousClose": 170.10,
                              "pctChange1d": 1.97,
                              "sma200": 158.40,
                              "rsi14": 58.3,
                              "volume": 12400000
                            }
                            """)
                    .build());
        });

        Optional<PriceFactsResponse> result = adapter.fetchPriceFacts("AAPL");

        assertTrue(result.isPresent());
        PriceFactsResponse facts = result.get();
        assertEquals("AAPL", facts.ticker());
        assertEquals(252, facts.barsAvailable());
        assertEquals(new BigDecimal("173.45"), facts.close());
        assertEquals(new BigDecimal("158.40"), facts.sma200());
    }

    @Test
    void fetchPriceFactsReturnsEmptyOn404() {
        MarketDataServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        Optional<PriceFactsResponse> result = adapter.fetchPriceFacts("UNKNOWN");

        assertTrue(result.isEmpty());
    }

    @Test
    void fetchPriceFactsThrowsInsufficientHistoryOn422() {
        MarketDataServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {"error":"INSUFFICIENT_HISTORY","ticker":"NEWCO","barsAvailable":50,"barsRequired":200}
                                """)
                        .build()));

        InsufficientHistoryUpstreamException ex = assertThrows(
                InsufficientHistoryUpstreamException.class,
                () -> adapter.fetchPriceFacts("NEWCO"));
        assertEquals("NEWCO", ex.ticker());
    }

    @Test
    void fetchPriceFactsThrowsMarketDataUpstreamOn5xx() {
        MarketDataServiceAdapter adapter = createAdapter(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        MarketDataUpstreamException ex = assertThrows(
                MarketDataUpstreamException.class,
                () -> adapter.fetchPriceFacts("AAPL"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("AAPL"), "exception should mention ticker; got " + ex.getMessage());
    }

    private MarketDataServiceAdapter createAdapter(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        return new MarketDataServiceAdapter(webClient, "secret-x", Clock.fixed(java.time.Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC));
    }
}
