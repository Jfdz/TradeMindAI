package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicReasoningFallbackTest {

    private final DeterministicReasoningFallback fallback = new DeterministicReasoningFallback();

    @Test void lowBand_containsWeakSetup() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.30), null));
        assertThat(result).containsIgnoringCase("weak setup");
    }

    @Test void mediumBand_containsDirectionalText() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.55), null));
        assertThat(result).containsIgnoringCase("bullish breakout");
    }

    @Test void highBand_containsHighConviction() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.80), null));
        assertThat(result).containsIgnoringCase("high-conviction");
    }

    @Test void lowBand_nullConfidence_containsWeakSetup() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", null, null));
        assertThat(result).containsIgnoringCase("weak setup");
    }
}
