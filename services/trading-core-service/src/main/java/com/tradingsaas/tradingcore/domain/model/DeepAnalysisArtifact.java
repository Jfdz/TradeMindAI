package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The grounded multi-agent deep-analysis artifact ai-engine returns for a
 * signal: a bull/bear/risk debate plus the judge's verdict.
 *
 * <p>This is analysis, never an authoritative signal. {@code conviction}
 * relates the verdict to the deterministic CNN signal — {@code CONTRADICTS} is
 * the soft low-conviction flag surfaced for review; the signal value is never
 * mutated. A {@code refused} section has its {@code text} withheld (it failed
 * grounding or the model declined); {@code validatorViolations} records why.
 */
public record DeepAnalysisArtifact(
        String schemaVersion,
        String outcome,
        String ticker,
        String signalType,
        String verdictDirection,
        String conviction,
        Section verdict,
        List<Section> sections,
        String provider,
        String modelVersion,
        Instant generatedAt) {

    public record Section(
            String role,
            String text,
            List<String> priceRefs,
            List<String> newsRefs,
            boolean refused,
            String refusalReason,
            List<Map<String, Object>> validatorViolations) {}
}
