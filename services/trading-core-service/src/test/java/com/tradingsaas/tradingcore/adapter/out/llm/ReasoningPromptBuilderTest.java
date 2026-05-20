package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReasoningPromptBuilderTest {

    private final ReasoningPromptBuilder builder = new ReasoningPromptBuilder();

    @Test void nullConfidence_isLow() {
        assertThat(ReasoningPromptBuilder.bandOf(null))
                .isEqualTo(ReasoningPromptBuilder.Band.LOW);
    }

    @Test void confidence039_isLow() {
        assertThat(ReasoningPromptBuilder.bandOf(BigDecimal.valueOf(0.39)))
                .isEqualTo(ReasoningPromptBuilder.Band.LOW);
    }

    @Test void confidence040_isMedium() {
        assertThat(ReasoningPromptBuilder.bandOf(BigDecimal.valueOf(0.40)))
                .isEqualTo(ReasoningPromptBuilder.Band.MEDIUM);
    }

    @Test void confidence069_isMedium() {
        assertThat(ReasoningPromptBuilder.bandOf(BigDecimal.valueOf(0.69)))
                .isEqualTo(ReasoningPromptBuilder.Band.MEDIUM);
    }

    @Test void confidence070_isHigh() {
        assertThat(ReasoningPromptBuilder.bandOf(BigDecimal.valueOf(0.70)))
                .isEqualTo(ReasoningPromptBuilder.Band.HIGH);
    }

    @Test void lowBand_promptContainsCautious() {
        String prompt = builder.build(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.30), "news"));
        assertThat(prompt).containsIgnoringCase("cautious");
    }

    @Test void mediumBand_promptContainsBalanced() {
        String prompt = builder.build(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.55), "news"));
        assertThat(prompt).containsIgnoringCase("balanced");
    }

    @Test void highBand_promptContainsAssertive() {
        String prompt = builder.build(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.80), "news"));
        assertThat(prompt).containsIgnoringCase("assertive");
    }

    @Test void highBand_promptContainsHighConviction() {
        String prompt = builder.build(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.80), "news"));
        assertThat(prompt).containsIgnoringCase("high-conviction");
    }

    @Test void allBands_promptContainsSummarizerFraming() {
        for (double conf : new double[] {0.30, 0.55, 0.80}) {
            String prompt = builder.build(
                    new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(conf), "news"));
            assertThat(prompt)
                    .as("framing missing for confidence=%s", conf)
                    .containsIgnoringCase("financial-data summarizer")
                    .containsIgnoringCase("not giving advice")
                    .containsIgnoringCase("Do not refuse");
        }
    }
}
