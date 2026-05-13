package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradingSignalRepository {

    TradingSignal save(TradingSignal signal);

    Page<TradingSignal> findAll(Pageable pageable);

    Optional<TradingSignal> findById(UUID id);

    Optional<TradingSignal> findLatest();

    void updateReasoning(UUID id, String reasoning, ReasoningStatus status, Instant reasoningGeneratedAt);

    /**
     * Attaches (or replaces) the full {@link ReasoningArtifact} audit blob
     * on an existing signal. Also updates the high-level
     * {@code reasoning}/{@code reasoning_status}/{@code reasoning_generated_at}
     * fields so the UI and the audit view stay consistent.
     *
     * <p>Returns {@code true} when the signal exists and the update was
     * applied; {@code false} when no row matched the id.
     */
    boolean updateReasoningArtifact(
            UUID id,
            String reasoning,
            ReasoningStatus status,
            Instant reasoningGeneratedAt,
            ReasoningArtifact artifact);

    /**
     * Find the most recent signal that matches every duplication-key field. Used by
     * {@code SignalGenerationService} to short-circuit insert when the cron (or any
     * other producer) re-publishes the same prediction within {@code sinceAtLeast}.
     *
     * <p>{@code entryPrice} is part of the duplication key: a same-day signal with a
     * different entry price is intentionally NOT a duplicate. {@code null} entry
     * price collides with {@code null}.
     */
    Optional<TradingSignal> findRecentEquivalent(
            String ticker,
            SignalType signalType,
            Timeframe timeframe,
            BigDecimal entryPrice,
            Instant sinceAtLeast);
}
