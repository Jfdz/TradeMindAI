package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ReasoningPromptBuilder {

    public enum Band { LOW, MEDIUM, HIGH }

    private static final String LOW_PROMPT =
            "Write one cautious sentence (max 280 chars) noting the weak / "
            + "low-conviction %s setup on %s at %.0f%% confidence. Mention lack of "
            + "strong confirmation. Recent news: %s. No disclaimers.";

    private static final String MEDIUM_PROMPT =
            "Write one sentence (max 280 chars) describing the balanced %s setup "
            + "on %s at %.0f%% confidence. Cite a specific price-action cue or catalyst. "
            + "Recent news: %s. No disclaimers.";

    private static final String HIGH_PROMPT =
            "Write one assertive sentence (max 280 chars) describing the "
            + "high-conviction %s setup on %s at %.0f%% confidence. Lead with strongest "
            + "catalyst or technical trigger. Recent news: %s. No disclaimers.";

    public static Band bandOf(BigDecimal confidence) {
        if (confidence == null) return Band.LOW;
        double v = confidence.doubleValue();
        if (v < 0.40) return Band.LOW;
        if (v < 0.70) return Band.MEDIUM;
        return Band.HIGH;
    }

    public String build(ReasoningContext ctx) {
        String news = ctx.newsContext() != null ? ctx.newsContext() : "none";
        double confPct = ctx.confidence() != null ? ctx.confidence().doubleValue() * 100 : 0;
        String template = switch (bandOf(ctx.confidence())) {
            case LOW -> LOW_PROMPT;
            case MEDIUM -> MEDIUM_PROMPT;
            case HIGH -> HIGH_PROMPT;
        };
        return String.format(template, ctx.signalType(), ctx.ticker(), confPct, news);
    }
}
