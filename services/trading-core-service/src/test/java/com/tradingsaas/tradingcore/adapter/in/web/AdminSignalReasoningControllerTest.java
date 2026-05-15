package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningAuditResponse;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminSignalReasoningControllerTest {

    private final TradingSignalRepository repository = mock(TradingSignalRepository.class);
    private final AdminSignalReasoningController controller =
            new AdminSignalReasoningController(repository);

    private static ReasoningArtifact sampleArtifact() {
        return new ReasoningArtifact(
                "GENERATED", "anthropic_oauth", "claude-haiku-4-5", 0, null,
                Map.of("ticker", "META", "close", 603.0),
                List.of("sma_200", "rsi_14"),
                List.of("https://reuters.com/x"),
                List.of(),
                Map.of("input_tokens", 100, "output_tokens", 50));
    }

    private static TradingSignal signalWithArtifact(UUID id, ReasoningArtifact artifact) {
        return new TradingSignal(
                id, UUID.randomUUID(), "META", SignalType.BUY,
                new Confidence(new BigDecimal("0.6200")), Timeframe.DAILY,
                Instant.parse("2026-05-13T12:00:00Z"),
                null, null, new BigDecimal("4.5"), new BigDecimal("603.0"),
                "Price 603.0 above sma_200 (510.0).",
                ReasoningStatus.READY,
                Instant.parse("2026-05-13T12:00:30Z"),
                artifact);
    }

    @Test
    void returns200WithFullAuditWhenSignalHasArtifact() {
        UUID signalId = UUID.randomUUID();
        when(repository.findById(signalId))
                .thenReturn(Optional.of(signalWithArtifact(signalId, sampleArtifact())));

        ResponseEntity<ReasoningAuditResponse> response = controller.getReasoningAudit(signalId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReasoningAuditResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("META", body.ticker());
        assertEquals("GENERATED", body.outcome());
        assertEquals("anthropic_oauth", body.provider());
        assertEquals(List.of("sma_200", "rsi_14"), body.priceRefs());
        assertEquals(List.of("https://reuters.com/x"), body.newsRefs());
        assertEquals("META", body.factsSnapshot().get("ticker"));
        assertEquals(Instant.parse("2026-05-13T12:00:00Z"), body.signalGeneratedAt());
        assertEquals(0, new BigDecimal("603.0").compareTo(body.entryPrice()));
    }

    @Test
    void returns200WithNullArtifactFieldsWhenSignalHasNoArtifactYet() {
        UUID signalId = UUID.randomUUID();
        when(repository.findById(signalId))
                .thenReturn(Optional.of(signalWithArtifact(signalId, null)));

        ResponseEntity<ReasoningAuditResponse> response = controller.getReasoningAudit(signalId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReasoningAuditResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("META", body.ticker());
        // Audit fields all null but user-facing reasoning still present.
        assertEquals(null, body.outcome());
        assertEquals(null, body.provider());
        assertEquals(null, body.factsSnapshot());
        assertEquals("Price 603.0 above sma_200 (510.0).", body.reasoning());
        assertEquals(ReasoningStatus.READY, body.reasoningStatus());
        // signalGeneratedAt populated regardless of artifact presence.
        assertEquals(Instant.parse("2026-05-13T12:00:00Z"), body.signalGeneratedAt());
    }

    @Test
    void surfacesPartialArtifactFieldsWhenOutcomeIsNull() {
        UUID signalId = UUID.randomUUID();
        ReasoningArtifact partial = new ReasoningArtifact(
                null, "anthropic_oauth", "claude-haiku-4-5", 0, null,
                null, null, null, null, null);
        when(repository.findById(signalId))
                .thenReturn(Optional.of(signalWithArtifact(signalId, partial)));

        ResponseEntity<ReasoningAuditResponse> response = controller.getReasoningAudit(signalId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReasoningAuditResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(null, body.outcome());
        assertEquals("anthropic_oauth", body.provider());
        assertEquals("claude-haiku-4-5", body.modelVersion());
        assertEquals(Integer.valueOf(0), body.retryCount());
    }

    @Test
    void returns404WhenSignalDoesNotExist() {
        UUID signalId = UUID.randomUUID();
        when(repository.findById(signalId)).thenReturn(Optional.empty());

        ResponseEntity<ReasoningAuditResponse> response = controller.getReasoningAudit(signalId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
