package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DeterministicReasoningFallback {

    public String build(ReasoningContext ctx) {
        String confStr = ctx.confidence() != null
                ? ctx.confidence().multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP) + "%"
                : "—";
        String changeStr = ctx.newsContext() != null && !ctx.newsContext().isBlank()
                ? " | Context: " + truncate(ctx.newsContext(), 80)
                : "";
        return switch (ReasoningPromptBuilder.bandOf(ctx.confidence())) {
            case LOW -> String.format("Weak setup, awaiting confirmation for %s %s signal at %s confidence%s.",
                    ctx.ticker(), ctx.signalType(), confStr, changeStr);
            case MEDIUM -> String.format("%s %s with %s confidence%s.",
                    ctx.ticker(), resolveDirection(ctx.signalType()), confStr, changeStr);
            case HIGH -> String.format("High-conviction %s setup — strong catalyst detected. %s at %s confidence%s.",
                    ctx.signalType(), ctx.ticker(), confStr, changeStr);
        };
    }

    private String resolveDirection(String signalType) {
        if (signalType == null) return "signal detected";
        return switch (signalType.toUpperCase()) {
            case "BUY" -> "bullish breakout detected";
            case "SELL" -> "bearish breakdown detected";
            default -> "neutral position indicated";
        };
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
