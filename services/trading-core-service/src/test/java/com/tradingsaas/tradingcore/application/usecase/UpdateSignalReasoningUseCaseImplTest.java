package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.in.UpdateSignalReasoningUseCase;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateSignalReasoningUseCaseImplTest {

    private final TradingSignalRepository repository = mock(TradingSignalRepository.class);
    private final UpdateSignalReasoningUseCaseImpl useCase = new UpdateSignalReasoningUseCaseImpl(repository);

    private static ReasoningArtifact sampleArtifact() {
        return new ReasoningArtifact(
                "GENERATED", "anthropic_oauth", "claude-haiku-4-5", 0, null,
                Map.of("ticker", "META", "close", 603.0),
                List.of("sma_200"), List.of(), List.of(),
                Map.of("input_tokens", 100, "output_tokens", 50));
    }

    @Test
    void returnsUpdatedWhenRepositoryFindsSignal() {
        UUID signalId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-13T12:00:00Z");
        when(repository.updateReasoningArtifact(
                eq(signalId), any(), eq(ReasoningStatus.READY), eq(now), any()))
                .thenReturn(true);

        UpdateSignalReasoningUseCase.Outcome outcome = useCase.execute(
                signalId, "bullish setup", ReasoningStatus.READY, now, sampleArtifact());

        assertEquals(UpdateSignalReasoningUseCase.Outcome.UPDATED, outcome);
        verify(repository).updateReasoningArtifact(
                eq(signalId), eq("bullish setup"), eq(ReasoningStatus.READY), eq(now),
                any(ReasoningArtifact.class));
    }

    @Test
    void returnsSignalNotFoundWhenRepositoryReturnsFalse() {
        UUID signalId = UUID.randomUUID();
        when(repository.updateReasoningArtifact(any(), any(), any(), any(), any()))
                .thenReturn(false);

        UpdateSignalReasoningUseCase.Outcome outcome = useCase.execute(
                signalId, null, ReasoningStatus.FAILED, Instant.now(), sampleArtifact());

        assertEquals(UpdateSignalReasoningUseCase.Outcome.SIGNAL_NOT_FOUND, outcome);
    }

    @Test
    void rejectsNullArtifact() {
        UUID signalId = UUID.randomUUID();
        assertThrows(
                NullPointerException.class,
                () -> useCase.execute(signalId, "text", ReasoningStatus.READY, Instant.now(), null));
    }

    @Test
    void rejectsNullSignalId() {
        assertThrows(
                NullPointerException.class,
                () -> useCase.execute(null, "text", ReasoningStatus.READY, Instant.now(), sampleArtifact()));
    }
}
