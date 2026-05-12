package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicReasoningFallbackTest {

    private final DeterministicReasoningFallback fallback = new DeterministicReasoningFallback();

    @Test void lowBand_usesLowBandWording() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.30), null));
        assertThat(result).containsAnyOf("Weak", "Low-conviction", "Tentative");
        assertThat(result).contains("MSFT").contains("BUY").contains("30%");
    }

    @Test void mediumBand_usesMediumBandWording() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.55), null));
        assertThat(result).containsAnyOf("Balanced", "Moderate", "Neutral-to-positive");
        assertThat(result).contains("MSFT").contains("BUY").contains("55%");
    }

    @Test void highBand_usesHighBandWording() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", BigDecimal.valueOf(0.80), null));
        assertThat(result).containsAnyOf("High-conviction", "Strong", "Robust");
        assertThat(result).contains("MSFT").contains("BUY").contains("80%");
    }

    @Test void nullConfidence_stillRendersLowBand() {
        String result = fallback.build(new ReasoningContext("MSFT", "BUY", null, null));
        assertThat(result).containsAnyOf("Weak", "Low-conviction", "Tentative");
    }

    @Test void sameInputs_produceSameTemplate() {
        ReasoningContext ctx = new ReasoningContext("NVDA", "BUY", BigDecimal.valueOf(0.55), null);
        String first = fallback.build(ctx);
        String second = fallback.build(ctx);
        assertThat(first).isEqualTo(second);
    }

    @Test void differentTickers_canSelectDifferentTemplates() {
        // Three different tickers should hit at least 2 distinct variants in the MEDIUM band.
        Set<String> rendered = new HashSet<>();
        for (String ticker : new String[]{"AAPL", "MSFT", "NVDA", "TSLA", "GOOGL", "META", "AMZN", "AMD"}) {
            rendered.add(fallback.build(
                    new ReasoningContext(ticker, "BUY", BigDecimal.valueOf(0.55), null)));
        }
        assertThat(rendered).hasSizeGreaterThan(1);
    }

    @Test void newsContextInterpolatesIntoSuffix() {
        String result = fallback.build(new ReasoningContext(
                "TSLA", "BUY", BigDecimal.valueOf(0.55),
                "Tesla beats Q4 delivery estimates"));
        assertThat(result).contains("news:").contains("Tesla beats Q4 delivery estimates");
    }

    @Test void multipleHeadlines_usesFirstOnly() {
        String result = fallback.build(new ReasoningContext(
                "TSLA", "BUY", BigDecimal.valueOf(0.55),
                "First headline; Second headline; Third"));
        assertThat(result).contains("First headline");
        assertThat(result).doesNotContain("Second headline");
    }

    @Test void longHeadlineIsTruncated() {
        String longHeadline = "Tesla announces breakthrough battery technology that doubles range "
                + "and halves charging time in next-generation Model S vehicles";
        String result = fallback.build(new ReasoningContext(
                "TSLA", "BUY", BigDecimal.valueOf(0.55), longHeadline));
        assertThat(result).contains("…");
        assertThat(result.length()).isLessThanOrEqualTo(280);
    }

    @Test void blankNewsContextAddsNoSuffix() {
        String withoutNews = fallback.build(new ReasoningContext(
                "TSLA", "BUY", BigDecimal.valueOf(0.55), ""));
        assertThat(withoutNews).doesNotContain("news:");
    }
}
