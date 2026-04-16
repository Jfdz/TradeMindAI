package com.tradingsaas.marketdata.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.in.web.dto.IndicatorsResponse;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.port.in.GetIndicatorsUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IndicatorsControllerTest {

    private final GetIndicatorsUseCase useCase = mock(GetIndicatorsUseCase.class);
    private final IndicatorsController controller = new IndicatorsController(useCase);

    @Test
    void returnsOkWithIndicatorsWhenFound() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        TechnicalIndicator rsi = new TechnicalIndicator(
                symbol, LocalDate.of(2026, 4, 16), TechnicalIndicatorType.RSI, new BigDecimal("62.5"), Map.of());

        when(useCase.getLatestIndicators("AAPL", List.of(TechnicalIndicatorType.RSI)))
                .thenReturn(List.of(rsi));

        ResponseEntity<IndicatorsResponse> response =
                controller.getLatestIndicators("aapl", List.of(TechnicalIndicatorType.RSI));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AAPL", response.getBody().ticker());
        assertEquals(1, response.getBody().indicators().size());
        assertEquals(TechnicalIndicatorType.RSI, response.getBody().indicators().getFirst().type());
        assertEquals(new BigDecimal("62.5"), response.getBody().indicators().getFirst().value());
    }

    @Test
    void returnsNotFoundWhenNoIndicatorsExist() {
        when(useCase.getLatestIndicators("UNKNOWN", null)).thenReturn(List.of());

        ResponseEntity<IndicatorsResponse> response = controller.getLatestIndicators("UNKNOWN", null);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returnsAllIndicatorsWhenTypesParamAbsent() {
        Symbol symbol = new Symbol("TSLA", "Tesla Inc.", "NASDAQ");
        List<TechnicalIndicator> allIndicators = List.of(
                new TechnicalIndicator(symbol, LocalDate.of(2026, 4, 16), TechnicalIndicatorType.RSI, new BigDecimal("45.0"), Map.of()),
                new TechnicalIndicator(symbol, LocalDate.of(2026, 4, 16), TechnicalIndicatorType.SMA_20, new BigDecimal("200.0"), Map.of()));

        when(useCase.getLatestIndicators("TSLA", null)).thenReturn(allIndicators);

        ResponseEntity<IndicatorsResponse> response = controller.getLatestIndicators("TSLA", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().indicators().size());
    }
}
