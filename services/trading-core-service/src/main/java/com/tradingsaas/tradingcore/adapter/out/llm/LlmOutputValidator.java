package com.tradingsaas.tradingcore.adapter.out.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class LlmOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(LlmOutputValidator.class);

    private static final int MAX_CHARS = 280;

    // Catches refusal phrasings observed in production and near-variants. Compiled once.
    private static final List<Pattern> REFUSAL_PATTERNS = List.of(
            Pattern.compile("\\bI (can'?t|cannot|won'?t)\\b.*\\b("
                    + "answer|provide|generate|give|help|write|create|make|"
                    + "fulfill|comply|complete|do|respond|engage|assist|deliver|share|offer"
                    + ")\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("\\bI'?m (unable|not able|sorry|afraid)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(unable|refuse|decline) to (help|assist|answer|fulfill|comply|provide|engage|respond)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(investment|financial|trading)\\s+advice\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(as an?|I am an?|I'?m an?)\\s+(AI|assistant|language model|LLM)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnot (a )?(licensed|qualified|certified|professional) (financial|investment)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(language model|large language model)\\b", Pattern.CASE_INSENSITIVE));

    private final Counter okCounter;
    private final Counter refusalCounter;
    private final Counter tooLongCounter;
    private final Counter blankCounter;

    public LlmOutputValidator(MeterRegistry meterRegistry) {
        this.okCounter = Counter.builder("signal_reasoning_validation_total")
                .tag("result", "ok").register(meterRegistry);
        this.refusalCounter = Counter.builder("signal_reasoning_validation_total")
                .tag("result", "refusal").register(meterRegistry);
        this.tooLongCounter = Counter.builder("signal_reasoning_validation_total")
                .tag("result", "too_long").register(meterRegistry);
        this.blankCounter = Counter.builder("signal_reasoning_validation_total")
                .tag("result", "blank").register(meterRegistry);
    }

    public Optional<String> validate(String raw) {
        if (raw == null || raw.isBlank()) {
            blankCounter.increment();
            return Optional.empty();
        }
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_CHARS) {
            tooLongCounter.increment();
            return Optional.empty();
        }
        for (Pattern pattern : REFUSAL_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                refusalCounter.increment();
                log.warn("Rejected reasoning as refusal: pattern={} text=\"{}\"",
                        pattern.pattern(), preview(trimmed));
                return Optional.empty();
            }
        }
        okCounter.increment();
        return Optional.of(trimmed);
    }

    private static String preview(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
