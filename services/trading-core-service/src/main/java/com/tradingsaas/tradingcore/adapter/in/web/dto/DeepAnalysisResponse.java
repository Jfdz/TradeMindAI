package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Customer-facing deep-analysis payload. Mirrors {@link DeepAnalysisArtifact}
 * but decouples the wire contract from the domain record. A {@code refused}
 * section carries empty {@code text}; the UI renders a withheld placeholder.
 */
public record DeepAnalysisResponse(
        String schemaVersion,
        String outcome,
        String ticker,
        String signalType,
        Instant generatedAt,
        String verdictDirection,
        String conviction,
        SectionResponse verdict,
        List<SectionResponse> sections,
        String provider,
        String modelVersion) {

    public record SectionResponse(
            String role,
            String text,
            List<String> priceRefs,
            List<String> newsRefs,
            boolean refused,
            String refusalReason,
            List<Map<String, Object>> validatorViolations) {}

    public static DeepAnalysisResponse from(DeepAnalysisArtifact a) {
        return new DeepAnalysisResponse(
                a.schemaVersion(),
                a.outcome(),
                a.ticker(),
                a.signalType(),
                a.generatedAt(),
                a.verdictDirection(),
                a.conviction(),
                fromSection(a.verdict()),
                a.sections() == null
                        ? List.of()
                        : a.sections().stream().map(DeepAnalysisResponse::fromSection).toList(),
                a.provider(),
                a.modelVersion());
    }

    private static SectionResponse fromSection(DeepAnalysisArtifact.Section s) {
        if (s == null) {
            return null;
        }
        return new SectionResponse(
                s.role(),
                s.text(),
                s.priceRefs(),
                s.newsRefs(),
                s.refused(),
                s.refusalReason(),
                s.validatorViolations());
    }
}
