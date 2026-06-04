package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException;
import com.tradingsaas.tradingcore.domain.exception.SignalNotFoundException;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisSignalFacts;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisEnginePort;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeepAnalysisServiceTest {

    private static final UUID SIGNAL_ID = UUID.randomUUID();
    private static final Instant GENERATED_AT = Instant.parse("2026-05-13T12:00:00Z");

    private final TradingSignalJpaRepository signals = mock(TradingSignalJpaRepository.class);
    private final DeepAnalysisEnginePort engine = mock(DeepAnalysisEnginePort.class);
    private final DeepAnalysisRepository repository = mock(DeepAnalysisRepository.class);
    private final DeepAnalysisService service = new DeepAnalysisService(signals, engine, repository);

    private static TradingSignalJpaEntity signalEntity() {
        return new TradingSignalJpaEntity(
                SIGNAL_ID,
                UUID.randomUUID(),
                "META",
                SignalType.BUY,
                new BigDecimal("0.6200"),
                Timeframe.DAILY,
                GENERATED_AT,
                null,
                null,
                new BigDecimal("4.5000"),
                new BigDecimal("603.000000"));
    }

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
                List.of(),
                "minimax_oauth",
                "MiniMax-M2.5-highspeed",
                GENERATED_AT);
    }

    @Test
    void generateLoadsSignalCallsEnginePersistsAndReturns() {
        when(signals.findById(SIGNAL_ID)).thenReturn(Optional.of(signalEntity()));
        DeepAnalysisArtifact art = artifact();
        when(engine.generate(any())).thenReturn(art);

        DeepAnalysisArtifact result = service.generate(SIGNAL_ID);

        assertSame(art, result);
        ArgumentCaptor<DeepAnalysisSignalFacts> captor =
                ArgumentCaptor.forClass(DeepAnalysisSignalFacts.class);
        verify(engine).generate(captor.capture());
        DeepAnalysisSignalFacts facts = captor.getValue();
        assertEquals("META", facts.ticker());
        assertEquals("BUY", facts.signalType());
        assertEquals(new BigDecimal("0.6200"), facts.confidence());
        assertEquals(new BigDecimal("603.000000"), facts.entryPrice());
        verify(repository).save(SIGNAL_ID, art);
    }

    @Test
    void generateThrowsWhenSignalMissingAndNeverCallsEngine() {
        when(signals.findById(SIGNAL_ID)).thenReturn(Optional.empty());

        assertThrows(SignalNotFoundException.class, () -> service.generate(SIGNAL_ID));

        verifyNoInteractions(engine);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void generateDoesNotPersistWhenEngineUnavailable() {
        when(signals.findById(SIGNAL_ID)).thenReturn(Optional.of(signalEntity()));
        when(engine.generate(any()))
                .thenThrow(new DeepAnalysisUnavailableException("no verdict"));

        assertThrows(DeepAnalysisUnavailableException.class, () -> service.generate(SIGNAL_ID));

        verify(repository, never()).save(any(), any());
    }

    @Test
    void getDelegatesToRepository() {
        DeepAnalysisArtifact art = artifact();
        when(repository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.of(art));

        assertEquals(Optional.of(art), service.get(SIGNAL_ID));
    }
}
