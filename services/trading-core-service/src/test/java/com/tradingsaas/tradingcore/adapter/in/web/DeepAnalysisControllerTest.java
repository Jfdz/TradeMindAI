package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.DeepAnalysisResponse;
import com.tradingsaas.tradingcore.application.usecase.DeepAnalysisService;
import com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException;
import com.tradingsaas.tradingcore.domain.exception.SignalNotFoundException;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DeepAnalysisControllerTest {

    private static final UUID SIGNAL_ID = UUID.randomUUID();

    private final DeepAnalysisService service = mock(DeepAnalysisService.class);
    private final DeepAnalysisController controller = new DeepAnalysisController(service);

    private static DeepAnalysisArtifact artifact() {
        return new DeepAnalysisArtifact(
                "v1.0",
                "GENERATED",
                "META",
                "BUY",
                "BULLISH",
                "AGREES",
                new DeepAnalysisArtifact.Section(
                        "JUDGE", "bull edge", List.of("close"), List.of(), false, null, List.of()),
                List.of(
                        new DeepAnalysisArtifact.Section(
                                "BULL", "bull case", List.of("sma_200"), List.of(), false, null, List.of())),
                "minimax_oauth",
                "MiniMax-M2.5-highspeed",
                Instant.parse("2026-05-13T12:00:00Z"));
    }

    @Test
    void postGeneratesAndReturns200() {
        when(service.generate(SIGNAL_ID)).thenReturn(artifact());

        ResponseEntity<DeepAnalysisResponse> response = controller.generate(SIGNAL_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BULLISH", response.getBody().verdictDirection());
        assertEquals("AGREES", response.getBody().conviction());
        assertEquals(1, response.getBody().sections().size());
    }

    @Test
    void getReturns200WhenPresent() {
        when(service.get(SIGNAL_ID)).thenReturn(Optional.of(artifact()));

        ResponseEntity<DeepAnalysisResponse> response = controller.get(SIGNAL_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("META", response.getBody().ticker());
    }

    @Test
    void getReturns404WhenAbsent() {
        when(service.get(SIGNAL_ID)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.get(SIGNAL_ID).getStatusCode());
    }

    @Test
    void signalNotFoundMapsTo404() {
        ResponseEntity<Map<String, Object>> response =
                controller.handleSignalNotFound(new SignalNotFoundException(SIGNAL_ID));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("SIGNAL_NOT_FOUND", response.getBody().get("error"));
    }

    @Test
    void unavailableMapsTo503() {
        ResponseEntity<Map<String, Object>> response =
                controller.handleUnavailable(new DeepAnalysisUnavailableException("no verdict"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DEEP_ANALYSIS_UNAVAILABLE", response.getBody().get("error"));
    }
}
