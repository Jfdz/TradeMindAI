package com.tradingsaas.marketdata.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.in.web.dto.PriceFactsResponse;
import com.tradingsaas.marketdata.domain.exception.InsufficientHistoryException;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetPriceFactsUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PriceFactsControllerTest {

    private final GetPriceFactsUseCase useCase = mock(GetPriceFactsUseCase.class);
    private final PriceFactsController controller = new PriceFactsController(useCase);

    @Test
    void returnsSnapshotWhenAvailable() {
        PriceFacts facts = new PriceFacts(
                "META", TimeFrame.DAILY, LocalDate.of(2026, 5, 12), 220,
                new BigDecimal("603.00"), new BigDecimal("590.94"),
                new BigDecimal("2.0400"), null, null,
                new BigDecimal("638.00"), new BigDecimal("412.00"),
                new BigDecimal("595.10"), new BigDecimal("580.20"), new BigDecimal("510.00"),
                new BigDecimal("58.3"), new BigDecimal("1.2"),
                12_400_000L, new BigDecimal("14100000.00"),
                new BigDecimal("580.00"), new BigDecimal("620.00"));
        when(useCase.getPriceFacts("META", TimeFrame.DAILY)).thenReturn(Optional.of(facts));

        ResponseEntity<PriceFactsResponse> response = controller.getPriceFacts("META", TimeFrame.DAILY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("META", response.getBody().ticker());
        assertEquals(new BigDecimal("603.00"), response.getBody().close());
        assertEquals(220, response.getBody().barsAvailable());
    }

    @Test
    void returns404WhenSymbolNotTracked() {
        when(useCase.getPriceFacts("UNKNOWN", TimeFrame.DAILY)).thenReturn(Optional.empty());

        ResponseEntity<PriceFactsResponse> response = controller.getPriceFacts("UNKNOWN", TimeFrame.DAILY);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returns400WhenTimeframeIsNotDaily() {
        ResponseEntity<PriceFactsResponse> response = controller.getPriceFacts("AAPL", TimeFrame.HOUR_1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returns422OnInsufficientHistoryExceptionHandler() {
        InsufficientHistoryException ex = new InsufficientHistoryException("AAPL", 50, 200);

        ResponseEntity<Map<String, Object>> response = controller.handleInsufficientHistory(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_HISTORY", response.getBody().get("error"));
        assertEquals("AAPL", response.getBody().get("ticker"));
        assertEquals(50, response.getBody().get("barsAvailable"));
        assertEquals(200, response.getBody().get("barsRequired"));
    }
}
