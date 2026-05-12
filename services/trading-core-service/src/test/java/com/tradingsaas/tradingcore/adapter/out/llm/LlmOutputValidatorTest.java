package com.tradingsaas.tradingcore.adapter.out.llm;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmOutputValidatorTest {

    private final LlmOutputValidator validator = new LlmOutputValidator(new SimpleMeterRegistry());

    @Test void acceptsValidText() {
        assertThat(validator.validate("AAPL breaks above 200-day MA on iPhone demand.")).isPresent();
    }

    @Test void rejectsNull() { assertThat(validator.validate(null)).isEmpty(); }

    @Test void rejectsBlank() { assertThat(validator.validate("   ")).isEmpty(); }

    @Test void rejectsTextOver280Chars() {
        assertThat(validator.validate("A".repeat(281))).isEmpty();
    }

    @Test void accepts280CharText() {
        assertThat(validator.validate("A".repeat(280))).isPresent();
    }

    @Test void trimsWhitespace() {
        Optional<String> result = validator.validate("  TSLA surges on delivery beat.  ");
        assertThat(result).contains("TSLA surges on delivery beat.");
    }

    // Refusal phrasings observed in production
    @ParameterizedTest
    @ValueSource(strings = {
            "I can't answer that.",
            "I'm unable to provide specific investment advice or predictions.",
            "I can't provide a sentence that could be used as investment advice.",
            "I cannot answer questions about specific trades.",
            "I won't generate trading recommendations.",
            "I'm sorry, but I can't help with that.",
            "I'm afraid I cannot provide investment advice.",
            "I'm not able to write that sentence.",
            "As an AI, I do not give financial advice.",
            "As an assistant, I can't make trading calls.",
            "I am an AI language model and cannot give investment recommendations.",
            "I'm an LLM and refuse to opine on stocks.",
            "I am not a licensed financial advisor.",
            "I'm not a qualified investment professional.",
            "The model declines to comment on investment advice."
    })
    void rejectsKnownRefusalPhrasings(String refusal) {
        assertThat(validator.validate(refusal))
                .as("Should reject: \"%s\"", refusal)
                .isEmpty();
    }

    // Happy-path corpus that should NOT trip the refusal regexes
    @ParameterizedTest
    @ValueSource(strings = {
            "AAPL breaks above 200-day MA on iPhone demand.",
            "TSLA bullish breakout detected with 50% confidence on volume spike.",
            "NVDA 5-day momentum aligns with positive earnings revisions; entry at 219.",
            "META declined 3% on weak ad-revenue guidance; bearish setup at 42% confidence.",
            "BTC-USD consolidates above the 20-day EMA; balanced long bias.",
            "Weak signal: AMD shows divergent RSI without volume confirmation."
    })
    void acceptsValidReasoningStrings(String reasoning) {
        assertThat(validator.validate(reasoning))
                .as("Should accept: \"%s\"", reasoning)
                .isPresent();
    }
}
