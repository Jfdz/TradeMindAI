package com.tradingsaas.tradingcore.adapter.out.llm;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class LlmOutputValidatorTest {

    private final LlmOutputValidator validator = new LlmOutputValidator();

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

    @Test void rejectsBannedTokenAI() {
        assertThat(validator.validate("AI analysis suggests bullish momentum.")).isEmpty();
    }

    @Test void rejectsBannedTokenModel() {
        assertThat(validator.validate("The model predicts upside of 5%.")).isEmpty();
    }

    @Test void trimsWhitespace() {
        Optional<String> result = validator.validate("  TSLA surges on delivery beat.  ");
        assertThat(result).contains("TSLA surges on delivery beat.");
    }
}
