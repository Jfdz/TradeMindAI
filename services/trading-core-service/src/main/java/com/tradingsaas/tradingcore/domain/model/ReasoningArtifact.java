package com.tradingsaas.tradingcore.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Audit artifact produced by ai-engine for one reasoning attempt.
 *
 * <p>Attached to a {@link TradingSignal} after the C5 validated reasoning
 * pipeline runs. The fields here are what {@code /api/v1/admin/signals/{id}/reasoning-audit}
 * surfaces so a human can replay why a reasoning was emitted, refused by
 * the LLM, or rejected by the validator.
 *
 * <p>Schema-tolerant by design: the three JSONB-backed maps ({@link #factsSnapshot},
 * {@link #priceRefs} as a List, etc.) are stored opaquely so additive
 * changes to the ai-engine schema do not require a new migration. The
 * fixed-column fields (outcome, provider, model, retry, refusal) are the
 * audit anchor that should never change shape.
 *
 * <p>{@code outcome} is non-null when the artifact was written via the C6
 * ingest endpoint (validated by {@code SignalReasoningController} at the
 * wire boundary). It can be {@code null} when the artifact is reconstructed
 * from a DB row whose {@code reasoning_outcome} column was cleared (e.g.
 * operator-issued reset to re-run reasoning) but whose provider/model/snapshot
 * metadata remains. Downstream consumers must null-check.
 */
public record ReasoningArtifact(
        String outcome,
        String provider,
        String modelVersion,
        int retryCount,
        String refusalReason,
        Map<String, Object> factsSnapshot,
        List<String> priceRefs,
        List<String> newsRefs,
        List<Map<String, Object>> validatorViolations,
        Map<String, Object> rawAudit) {

    public ReasoningArtifact {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        priceRefs = priceRefs == null ? List.of() : List.copyOf(priceRefs);
        newsRefs = newsRefs == null ? List.of() : List.copyOf(newsRefs);
        validatorViolations = validatorViolations == null ? List.of() : List.copyOf(validatorViolations);
    }
}
