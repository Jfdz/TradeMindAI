package com.tradingsaas.tradingcore.domain.port.out;

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
