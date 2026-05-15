package com.tradingsaas.tradingcore.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SignalMathServiceTest {

    private final SignalMathService math = new SignalMathService();

    @Test
    void buyTarget_130_05_pct_4_returns_135_252() {
        BigDecimal target = math.calculateTargetPrice(
                SignalType.BUY, new BigDecimal("130.05"), new BigDecimal("4.00"));
        assertThat(target).isEqualByComparingTo(new BigDecimal("135.252000"));
    }

    @Test
    void sellTarget_100_pct_5_returns_95() {
        BigDecimal target = math.calculateTargetPrice(
                SignalType.SELL, new BigDecimal("100"), new BigDecimal("5"));
        assertThat(target).isEqualByComparingTo(new BigDecimal("95.000000"));
    }

    @Test
    void buyStopLoss_130_05_pct_2_returns_127_449() {
        BigDecimal stop = math.calculateStopLoss(
                SignalType.BUY, new BigDecimal("130.05"), new BigDecimal("2.00"));
        assertThat(stop).isEqualByComparingTo(new BigDecimal("127.449000"));
    }

    @Test
    void sellStopLoss_100_pct_2_returns_102() {
        BigDecimal stop = math.calculateStopLoss(
                SignalType.SELL, new BigDecimal("100"), new BigDecimal("2"));
        assertThat(stop).isEqualByComparingTo(new BigDecimal("102.000000"));
    }

    @Test
    void expectedMovePct_buy_130_05_to_135_252_returns_4_0000() {
        BigDecimal move = math.calculateExpectedMovePct(
                SignalType.BUY, new BigDecimal("130.05"), new BigDecimal("135.252000"));
        assertThat(move).isEqualByComparingTo(new BigDecimal("4.0000"));
    }

    @Test
    void coherence_buy_130_05_target_135_25_stop_127_45_does_not_throw() {
        assertThatCode(() -> math.validatePriceCoherence(
                SignalType.BUY,
                new BigDecimal("130.05"),
                new BigDecimal("135.252000"),
                new BigDecimal("127.449000")))
                .doesNotThrowAnyException();
    }

    @Test
    void coherence_buy_with_target_below_entry_throws() {
        assertThatThrownBy(() -> math.validatePriceCoherence(
                SignalType.BUY,
                new BigDecimal("130.05"),
                new BigDecimal("125.00"),
                new BigDecimal("127.45")))
                .isInstanceOf(SignalCoherenceException.class);
    }

    @Test
    void coherence_sell_target_below_entry_below_stop_does_not_throw() {
        assertThatCode(() -> math.validatePriceCoherence(
                SignalType.SELL,
                new BigDecimal("100"),
                new BigDecimal("95"),
                new BigDecimal("102")))
                .doesNotThrowAnyException();
    }

    @Test
    void coherence_sell_with_target_above_entry_throws() {
        assertThatThrownBy(() -> math.validatePriceCoherence(
                SignalType.SELL,
                new BigDecimal("100"),
                new BigDecimal("105"),
                new BigDecimal("102")))
                .isInstanceOf(SignalCoherenceException.class);
    }

    @Test
    void hold_returns_null_for_all_calculators() {
        BigDecimal entry = new BigDecimal("100");
        BigDecimal pct = new BigDecimal("4");
        assertThat(math.calculateTargetPrice(SignalType.HOLD, entry, pct)).isNull();
        assertThat(math.calculateStopLoss(SignalType.HOLD, entry, pct)).isNull();
        assertThat(math.calculateExpectedMovePct(SignalType.HOLD, entry, new BigDecimal("104"))).isNull();
    }

    @Test
    void null_entry_returns_null_for_target_and_stop() {
        assertThat(math.calculateTargetPrice(SignalType.BUY, null, new BigDecimal("4"))).isNull();
        assertThat(math.calculateStopLoss(SignalType.BUY, null, new BigDecimal("2"))).isNull();
    }
}
