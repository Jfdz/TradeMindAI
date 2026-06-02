package com.tradingsaas.tradingcore.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignalPerformanceEvaluatorTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-15T00:00:00Z"), ZoneOffset.UTC);

    private final SignalPerformanceEvaluator evaluator = new SignalPerformanceEvaluator(CLOCK);

    private static OhlcvBar bar(String date, double open, double high, double low, double close) {
        return new OhlcvBar(Instant.parse(date + "T00:00:00Z"), open, high, low, close, 1_000L);
    }

    private SignalPerformance evalBuy(BigDecimal entry, BigDecimal target, BigDecimal stop, List<OhlcvBar> bars) {
        return evaluator.evaluate(UUID.randomUUID(), "AAPL", SignalType.BUY, GENERATED_AT, entry, target, stop, bars);
    }

    @Test
    void buyResolvesToWinWhenHighTouchesTargetBeforeStop() {
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("104"), new BigDecimal("98"),
                List.of(
                        bar("2026-01-02", 100, 103, 99, 102),   // no touch
                        bar("2026-01-03", 102, 105, 101, 104))); // target touched (high 105 >= 104)

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.WIN);
        assertThat(perf.resolvedAt()).isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
    }

    @Test
    void buyResolvesToLossWhenLowTouchesStop() {
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("104"), new BigDecimal("98"),
                List.of(bar("2026-01-02", 100, 101, 97, 99))); // low 97 <= stop 98

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.LOSS);
        assertThat(perf.resolvedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void straddleBarResolvesToLossConservatively() {
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("104"), new BigDecimal("98"),
                List.of(bar("2026-01-02", 100, 105, 97, 101))); // both target and stop in range

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.LOSS);
    }

    @Test
    void staysOpenWhenNeitherTouched() {
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("104"), new BigDecimal("98"),
                List.of(
                        bar("2026-01-02", 100, 103, 99, 102),
                        bar("2026-01-03", 102, 103, 99, 101)));

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.OPEN);
        assertThat(perf.resolvedAt()).isNull();
    }

    @Test
    void sellOutcomeIsInverted() {
        SignalPerformance perf = evaluator.evaluate(
                UUID.randomUUID(), "AAPL", SignalType.SELL, GENERATED_AT,
                new BigDecimal("100"), new BigDecimal("96"), new BigDecimal("102"),
                List.of(bar("2026-01-02", 100, 101, 95, 97))); // low 95 <= target 96 -> WIN

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.WIN);
    }

    @Test
    void sellResolvesToLossWhenHighTouchesStop() {
        SignalPerformance perf = evaluator.evaluate(
                UUID.randomUUID(), "AAPL", SignalType.SELL, GENERATED_AT,
                new BigDecimal("100"), new BigDecimal("96"), new BigDecimal("102"),
                List.of(bar("2026-01-02", 100, 103, 99, 101))); // high 103 >= stop 102 -> LOSS

        assertThat(perf.outcome()).isEqualTo(SignalOutcome.LOSS);
    }

    @Test
    void excursionIsDirectionAwareForBuy() {
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("999"), new BigDecimal("1"), // never touched
                List.of(
                        bar("2026-01-02", 100, 110, 98, 105),   // +10% high, -2% low
                        bar("2026-01-03", 105, 106, 93, 95)));  // -7% low

        assertThat(perf.maxProfit()).isEqualByComparingTo(new BigDecimal("0.1000"));   // (110-100)/100
        assertThat(perf.maxDrawdown()).isEqualByComparingTo(new BigDecimal("-0.0700")); // (93-100)/100
    }

    @Test
    void priceOffsetsSkipWeekendsToNextTradingBar() {
        // GENERATED_AT is Thursday 2026-01-01. +1 calendar day = Fri 2026-01-02;
        // here the next available bar is Friday, so price_1d = Friday close.
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("999"), new BigDecimal("1"),
                List.of(
                        bar("2026-01-02", 100, 101, 99, 100.5),   // Fri  -> price_1d
                        bar("2026-01-05", 100, 101, 99, 103.0))); // Mon  -> price_3d (skips Sat/Sun)

        assertThat(perf.price1d()).isEqualByComparingTo(new BigDecimal("100.5"));
        assertThat(perf.price3d()).isEqualByComparingTo(new BigDecimal("103.0"));
        assertThat(perf.price30d()).isNull(); // not enough history
    }

    @Test
    void barsBeforeGenerationAreIgnored() {
        List<OhlcvBar> bars = new ArrayList<>();
        bars.add(bar("2026-01-01", 100, 200, 1, 100)); // same day, at 00:00 -> before 12:00 generation
        bars.add(bar("2026-01-02", 100, 103, 99, 102));
        SignalPerformance perf = evalBuy(
                new BigDecimal("100"), new BigDecimal("104"), new BigDecimal("98"), bars);

        // The pre-generation bar's extreme high/low must not leak into the result.
        assertThat(perf.outcome()).isEqualTo(SignalOutcome.OPEN);
        assertThat(perf.maxProfit()).isEqualByComparingTo(new BigDecimal("0.0300"));
    }
}
