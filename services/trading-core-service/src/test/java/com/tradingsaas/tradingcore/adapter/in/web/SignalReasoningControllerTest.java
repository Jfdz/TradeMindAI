package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.UpdateReasoningRequest;
import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.in.UpdateSignalReasoningUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SignalReasoningControllerTest {

    private final UpdateSignalReasoningUseCase useCase = mock(UpdateSignalReasoningUseCase.class);
    private final SignalReasoningController controller = new SignalReasoningController(useCase);

    private static UpdateReasoningRequest validRequest() {
        return new UpdateReasoningRequest(
                "Price 603.0 above sma_200 (510.0). Constructive trend.",
                null, // status inferred from outcome
                "2026-05-13T12:00:00Z",
                "GENERATED",
                "anthropic_oauth",
                "claude-haiku-4-5",
                0,
                null,
                Map.of("ticker", "META"),
                List.of("sma_200"),
                List.of(),
                List.of(),
                Map.of("input_tokens", 100));
    }

    @Test
    void returns204OnSuccessfulUpdate() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(eq(signalId), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);

        ResponseEntity<?> response = controller.updateReasoning(signalId, validRequest());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returns404WhenSignalDoesNotExist() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.SIGNAL_NOT_FOUND);

        ResponseEntity<?> response = controller.updateReasoning(signalId, validRequest());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returns400WhenOutcomeMissing() {
        UUID signalId = UUID.randomUUID();
        UpdateReasoningRequest req = new UpdateReasoningRequest(
                "text", null, null, null, "anthropic_oauth", "claude-haiku-4-5",
                0, null, null, null, null, null, null);

        ResponseEntity<?> response = controller.updateReasoning(signalId, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(useCase, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void returns400WhenReasoningGeneratedAtIsNotIso8601() {
        UUID signalId = UUID.randomUUID();
        UpdateReasoningRequest req = new UpdateReasoningRequest(
                "text", null, "yesterday", "GENERATED", "anthropic_oauth",
                "claude-haiku-4-5", 0, null, null, null, null, null, null);

        ResponseEntity<?> response = controller.updateReasoning(signalId, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(useCase, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void infersReasoningStatusReadyFromGeneratedOutcome() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);

        controller.updateReasoning(signalId, validRequest());

        ArgumentCaptor<ReasoningStatus> statusCaptor = ArgumentCaptor.forClass(ReasoningStatus.class);
        verify(useCase).execute(eq(signalId), any(), statusCaptor.capture(), any(), any());
        assertEquals(ReasoningStatus.READY, statusCaptor.getValue());
    }

    @Test
    void infersReasoningStatusFallbackFromRefusedLlmDisabledOutcome() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);
        UpdateReasoningRequest req = new UpdateReasoningRequest(
                null, null, "2026-05-13T12:00:00Z", "REFUSED_LLM_DISABLED",
                "stub", "claude-haiku-4-5", 0,
                "llm_provider_is_stub", Map.of("ticker", "META"),
                List.of(), List.of(), List.of(), null);

        controller.updateReasoning(signalId, req);

        ArgumentCaptor<ReasoningStatus> statusCaptor = ArgumentCaptor.forClass(ReasoningStatus.class);
        verify(useCase).execute(eq(signalId), any(), statusCaptor.capture(), any(), any());
        assertEquals(ReasoningStatus.FALLBACK, statusCaptor.getValue());
    }

    @Test
    void infersReasoningStatusFailedFromRefusedByValidatorOutcome() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);
        UpdateReasoningRequest req = new UpdateReasoningRequest(
                null, null, "2026-05-13T12:00:00Z", "REFUSED_BY_VALIDATOR",
                "anthropic_oauth", "claude-haiku-4-5", 1, "ungrounded_number",
                Map.of("ticker", "META"), List.of(), List.of(),
                List.of(Map.of("type", "ungrounded_number", "detail", "900.0 not in facts")),
                Map.of("input_tokens", 100));

        controller.updateReasoning(signalId, req);

        ArgumentCaptor<ReasoningStatus> statusCaptor = ArgumentCaptor.forClass(ReasoningStatus.class);
        verify(useCase).execute(eq(signalId), any(), statusCaptor.capture(), any(), any());
        assertEquals(ReasoningStatus.FAILED, statusCaptor.getValue());
    }

    @Test
    void honorsExplicitReasoningStatusWhenProvided() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);
        UpdateReasoningRequest req = new UpdateReasoningRequest(
                "text", "PENDING", "2026-05-13T12:00:00Z", "GENERATED",
                "stub", "claude-haiku-4-5", 0, null,
                null, null, null, null, null);

        controller.updateReasoning(signalId, req);

        ArgumentCaptor<ReasoningStatus> statusCaptor = ArgumentCaptor.forClass(ReasoningStatus.class);
        verify(useCase).execute(eq(signalId), any(), statusCaptor.capture(), any(), any());
        assertEquals(ReasoningStatus.PENDING, statusCaptor.getValue());
    }

    @Test
    void buildsArtifactWithAllJsonFieldsForwarded() {
        UUID signalId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any()))
                .thenReturn(UpdateSignalReasoningUseCase.Outcome.UPDATED);

        controller.updateReasoning(signalId, validRequest());

        ArgumentCaptor<ReasoningArtifact> captor = ArgumentCaptor.forClass(ReasoningArtifact.class);
        verify(useCase).execute(any(), any(), any(), any(Instant.class), captor.capture());
        ReasoningArtifact captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("GENERATED", captured.outcome());
        assertEquals("anthropic_oauth", captured.provider());
        assertEquals("claude-haiku-4-5", captured.modelVersion());
        assertEquals(0, captured.retryCount());
        assertEquals(List.of("sma_200"), captured.priceRefs());
        assertEquals("META", captured.factsSnapshot().get("ticker"));
    }
}
