package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin view of a signal's reasoning artifact: surfaces the C5 audit
 * trail (facts snapshot, citations, validator violations, provider/model
 * metadata) alongside the user-facing text + status.
 *
 * <p>When the signal has no artifact yet ({@code artifact == null}), all
 * the JSON fields are null but the user-facing reasoning/status fields
 * still render so an operator can see which signals are still pending
 * vs already audited.
 */
public record ReasoningAuditResponse(
        UUID signalId,
        String ticker,
        ReasoningStatus reasoningStatus,
        String reasoning,
        Instant reasoningGeneratedAt,
        Instant signalGeneratedAt,
        BigDecimal entryPrice,
        BigDecimal targetPrice,
        BigDecimal stopLoss,
        BigDecimal expectedMovePct,
        String outcome,
        String provider,
        String modelVersion,
        Integer retryCount,
        String refusalReason,
        Map<String, Object> factsSnapshot,
        List<String> priceRefs,
        List<String> newsRefs,
        List<Map<String, Object>> validatorViolations,
        Map<String, Object> rawAudit) {

    public static ReasoningAuditResponse from(TradingSignal signal) {
        ReasoningArtifact a = signal.getReasoningArtifact();
        return new ReasoningAuditResponse(
                signal.getId(),
                signal.getTicker(),
                signal.getReasoningStatus(),
                signal.getReasoning(),
                signal.getReasoningGeneratedAt(),
                signal.getGeneratedAt(),
                signal.getEntryPrice(),
                signal.getTargetPrice(),
                signal.getStopLoss(),
                signal.getExpectedMovePct(),
                a == null ? null : a.outcome(),
                a == null ? null : a.provider(),
                a == null ? null : a.modelVersion(),
                a == null ? null : a.retryCount(),
                a == null ? null : a.refusalReason(),
                a == null ? null : a.factsSnapshot(),
                a == null ? null : a.priceRefs(),
                a == null ? null : a.newsRefs(),
                a == null ? null : a.validatorViolations(),
                a == null ? null : a.rawAudit());
    }
}
