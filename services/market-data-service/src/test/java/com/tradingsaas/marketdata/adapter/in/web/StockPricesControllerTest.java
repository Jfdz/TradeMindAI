package com.tradingsaas.marketdata.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.in.web.dto.PagedResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.StockPriceResponse;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetHistoricalPricesUseCase;
import com.tradingsaas.marketdata.domain.port.in.GetLatestPriceUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class StockPricesControllerTest {

    private final GetHistoricalPricesUseCase useCase = mock(GetHistoricalPricesUseCase.class);
    private final GetLatestPriceUseCase getLatestPriceUseCase = mock(GetLatestPriceUseCase.class);
    private final StockPricesController controller = new StockPricesController(useCase, getLatestPriceUseCase);

    private final LocalDate from = LocalDate.of(2026, 1, 1);
    private final LocalDate to = LocalDate.of(2026, 4, 16);

    @Test
    void returnsPagedPricesWhenDataExists() {
        StockPrice price = price("AAPL", to);
        Page<StockPrice> page = new PageImpl<>(List.of(price), PageRequest.of(0, 20), 1);
        when(useCase.getHistoricalPrices(eq("AAPL"), eq(TimeFrame.DAILY), eq(from), eq(to), any()))
                .thenReturn(page);

        ResponseEntity<PagedResponse<StockPriceResponse>> response =
                controller.getHistory("AAPL", TimeFrame.DAILY, from, to, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().content().size());
        assertEquals("AAPL", response.getBody().content().getFirst().ticker());
    }

    @Test
    void returns404WhenNoDataFound() {
        when(useCase.getHistoricalPrices(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        ResponseEntity<PagedResponse<StockPriceResponse>> response =
                controller.getHistory("UNKNOWN", TimeFrame.DAILY, from, to, 0, 20);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returns400WhenFromAfterTo() {
        ResponseEntity<PagedResponse<StockPriceResponse>> response =
                controller.getHistory("AAPL", TimeFrame.DAILY, to, from, 0, 20);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returnsLatestPricesForBatchRequest() {
        when(getLatestPriceUseCase.getLatestPrices(List.of("AAPL", "MSFT"), TimeFrame.DAILY))
                .thenReturn(List.of(price("AAPL", to), price("MSFT", to)));

        ResponseEntity<StockPricesController.LatestPricesResponse> response =
                controller.getLatestBatch(List.of("AAPL", "MSFT"), TimeFrame.DAILY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().prices().size());
        assertEquals("AAPL", response.getBody().prices().getFirst().ticker());
    }

    private static StockPrice price(String ticker, LocalDate date) {
        Symbol symbol = new Symbol(ticker, "Apple Inc.", "NASDAQ");
        OHLCV ohlcv = new OHLCV(new BigDecimal("170"), new BigDecimal("175"),
                new BigDecimal("168"), new BigDecimal("172"), 1_000_000L);
        return new StockPrice(symbol, date, TimeFrame.DAILY, ohlcv);
    }
}
