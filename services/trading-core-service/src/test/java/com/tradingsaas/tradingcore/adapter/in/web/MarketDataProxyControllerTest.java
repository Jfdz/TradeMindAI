package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataPage;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketPricePageResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketPriceResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketSymbolPageResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketSymbolResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.Ohlcv;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MarketDataProxyControllerTest {

    private final MarketDataServiceAdapter adapter = mock(MarketDataServiceAdapter.class);
    private final MarketDataProxyController controller = new MarketDataProxyController(adapter);

    @Test
    void returnsLatestPriceFromInternalMarketDataService() {
        MarketPriceResponse price = new MarketPriceResponse(
                "AAPL",
                LocalDate.of(2026, 4, 29),
                "DAILY",
                new Ohlcv(170.0, 175.0, 169.0, 174.0, 1000),
                new BigDecimal("174.0"));
        when(adapter.fetchLatestPrice("AAPL", "DAILY")).thenReturn(Optional.of(price));

        ResponseEntity<MarketPriceResponse> response = controller.latestPrice("AAPL", "DAILY");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(price, response.getBody());
    }

    @Test
    void returnsNotFoundWhenLatestPriceMissing() {
        when(adapter.fetchLatestPrice("UNKNOWN", "DAILY")).thenReturn(Optional.empty());

        ResponseEntity<MarketPriceResponse> response = controller.latestPrice("UNKNOWN", "DAILY");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void rejectsHistoricalRangeWhenFromAfterTo() {
        ResponseEntity<MarketDataPage<MarketPriceResponse>> response = controller.historicalPrices(
                "AAPL", "DAILY", LocalDate.of(2026, 4, 29), LocalDate.of(2026, 1, 1), 0, 20);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void clampsHistoricalPageSizeBeforeDelegating() {
        MarketPricePageResponse page = new MarketPricePageResponse(List.of(), 0, 100, 0, 0);
        when(adapter.fetchHistoricalPrices("AAPL", "DAILY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 29), 0, 100))
                .thenReturn(page);

        ResponseEntity<MarketDataPage<MarketPriceResponse>> response = controller.historicalPrices(
                "AAPL", "DAILY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 29), 0, 500);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100, response.getBody().size());
    }

    @Test
    void clampsSymbolsPageSizeBeforeDelegating() {
        MarketSymbolPageResponse page = new MarketSymbolPageResponse(
                List.of(new MarketSymbolResponse("AAPL", "Apple Inc.", "NASDAQ", "Technology", true)), 0, 100, 1, 1);
        when(adapter.fetchSymbols(0, 100)).thenReturn(page);

        MarketDataPage<MarketSymbolResponse> response = controller.symbols(0, 250);

        assertEquals(1, response.content().size());
        verify(adapter).fetchSymbols(0, 100);
    }
}
