package com.tradingsaas.tradingcore.application.service;

import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Deterministic price math for trading signals. Every number the user sees in
 * the UI and every number the LLM is allowed to mention as a "target" or "stop"
 * is computed here, never inferred by the model.
 */
@Service
public class SignalMathService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int PRICE_SCALE = 6;
    private static final int PCT_SCALE = 4;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public BigDecimal calculateTargetPrice(SignalType type, BigDecimal entry, BigDecimal pct) {
        if (entry == null || pct == null || type == null || type == SignalType.HOLD) {
            return null;
        }
        BigDecimal ratio = pct.divide(ONE_HUNDRED, MC);
        BigDecimal multiplier = switch (type) {
            case BUY -> BigDecimal.ONE.add(ratio, MC);
            case SELL -> BigDecimal.ONE.subtract(ratio, MC);
            default -> null;
        };
        if (multiplier == null) {
            return null;
        }
        return entry.multiply(multiplier, MC).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateStopLoss(SignalType type, BigDecimal entry, BigDecimal pct) {
        if (entry == null || pct == null || type == null || type == SignalType.HOLD) {
            return null;
        }
        BigDecimal ratio = pct.divide(ONE_HUNDRED, MC);
        BigDecimal multiplier = switch (type) {
            case BUY -> BigDecimal.ONE.subtract(ratio, MC);
            case SELL -> BigDecimal.ONE.add(ratio, MC);
            default -> null;
        };
        if (multiplier == null) {
            return null;
        }
        return entry.multiply(multiplier, MC).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateExpectedMovePct(SignalType type, BigDecimal entry, BigDecimal target) {
        if (entry == null || target == null || type == null || type == SignalType.HOLD) {
            return null;
        }
        if (entry.signum() == 0) {
            return null;
        }
        BigDecimal diff = target.subtract(entry, MC).abs(MC);
        return diff.divide(entry, MC)
                .multiply(ONE_HUNDRED, MC)
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    public void validatePriceCoherence(SignalType type,
                                       BigDecimal entry,
                                       BigDecimal target,
                                       BigDecimal stopLoss) {
        if (type == null || type == SignalType.HOLD) return;
        if (entry == null || target == null || stopLoss == null) return;
        switch (type) {
            case BUY -> {
                if (!(target.compareTo(entry) > 0 && entry.compareTo(stopLoss) > 0)) {
                    throw new SignalCoherenceException(
                            "BUY signal violates target > entry > stop: target=" + target
                                    + " entry=" + entry + " stop=" + stopLoss);
                }
            }
            case SELL -> {
                if (!(target.compareTo(entry) < 0 && entry.compareTo(stopLoss) < 0)) {
                    throw new SignalCoherenceException(
                            "SELL signal violates target < entry < stop: target=" + target
                                    + " entry=" + entry + " stop=" + stopLoss);
                }
            }
            default -> { /* HOLD already returned */ }
        }
    }
}
