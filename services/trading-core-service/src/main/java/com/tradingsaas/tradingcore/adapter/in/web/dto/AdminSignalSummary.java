package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight projection of a {@link TradingSignal} for the admin
 * reasoning-audit explorer table. Excludes the full audit artifact —
 * callers click into the detail view for that, which hits
 * {@code GET /api/v1/admin/signals/{id}/reasoning-audit}.
 */
public record AdminSignalSummary(
        UUID id,
        String ticker,
        SignalType signalType,
        BigDecimal confidence,
        Timeframe timeframe,
        Instant generatedAt,
        BigDecimal entryPrice,
        BigDecimal targetPrice,
        BigDecimal predictedChangePct,
        ReasoningStatus reasoningStatus,
        Instant reasoningGeneratedAt,
        String reasoningOutcome,
        String reasoningProvider,
        Integer reasoningRetryCount,
        boolean hasArtifact) {

    public static AdminSignalSummary from(TradingSignal signal) {
        var artifact = signal.getReasoningArtifact();
        return new AdminSignalSummary(
                signal.getId(),
                signal.getTicker(),
                signal.getType(),
                signal.getConfidence() != null ? signal.getConfidence().getValue() : null,
                signal.getTimeframe(),
                signal.getGeneratedAt(),
                signal.getEntryPrice(),
                signal.getTargetPrice(),
                signal.getPredictedChangePct(),
                signal.getReasoningStatus(),
                signal.getReasoningGeneratedAt(),
                artifact != null ? artifact.outcome() : null,
                artifact != null ? artifact.provider() : null,
                artifact != null ? Integer.valueOf(artifact.retryCount()) : null,
                artifact != null);
    }
}
