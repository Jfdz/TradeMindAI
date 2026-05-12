package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DeterministicReasoningFallback {

    // Three variants per band so the dashboard doesn't feel like one repeated string.
    // Format args: signalType, ticker, confidence, optional " — news: <headline>" suffix.
    private static final Map<ReasoningPromptBuilder.Band, List<String>> TEMPLATES = Map.of(
            ReasoningPromptBuilder.Band.LOW, List.of(
                    "Weak %s signal on %s at %s — awaiting confirmation%s.",
                    "Low-conviction %s setup on %s (confidence %s); momentum unconfirmed%s.",
                    "Tentative %s read on %s at %s — broader market context mixed%s."
            ),
            ReasoningPromptBuilder.Band.MEDIUM, List.of(
                    "Balanced %s setup on %s at %s confidence — recent flow constructive%s.",
                    "Moderate %s signal on %s (confidence %s); technicals and catalysts align partially%s.",
                    "Neutral-to-positive %s setup on %s at %s — watching for follow-through%s."
            ),
            ReasoningPromptBuilder.Band.HIGH, List.of(
                    "High-conviction %s on %s at %s — multiple indicators align%s.",
                    "Strong %s read on %s at %s confidence — momentum and breadth supportive%s.",
                    "Robust %s setup on %s at %s — trend, volume and catalysts converge%s."
            )
    );

    public String build(ReasoningContext ctx) {
        ReasoningPromptBuilder.Band band = ReasoningPromptBuilder.bandOf(ctx.confidence());
        List<String> variants = TEMPLATES.get(band);
        String template = variants.get(Math.floorMod(seed(ctx), variants.size()));
        String confStr = formatConfidence(ctx.confidence());
        String newsSuffix = formatNewsSuffix(ctx.newsContext());
        return String.format(template, signalTypeLabel(ctx.signalType()), ctx.ticker(), confStr, newsSuffix);
    }

    private int seed(ReasoningContext ctx) {
        // Deterministic per (ticker, signalType, confidence band) so refreshes show the same template.
        return Objects.hash(ctx.ticker(), ctx.signalType(),
                ctx.confidence() == null ? null : ctx.confidence().setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String formatConfidence(BigDecimal confidence) {
        if (confidence == null) return "—";
        return confidence.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP) + "%";
    }

    private String formatNewsSuffix(String newsContext) {
        if (newsContext == null || newsContext.isBlank()) return "";
        String headline = newsContext.split(";")[0].strip();
        if (headline.isBlank()) return "";
        return " — news: \"" + truncate(headline, 80) + "\"";
    }

    private String signalTypeLabel(String signalType) {
        if (signalType == null) return "signal";
        return switch (signalType.toUpperCase()) {
            case "BUY" -> "BUY";
            case "SELL" -> "SELL";
            case "HOLD" -> "HOLD";
            default -> signalType;
        };
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
