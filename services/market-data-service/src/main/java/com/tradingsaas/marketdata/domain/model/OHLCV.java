package com.tradingsaas.marketdata.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Open-high-low-close-volume market bar.
 */
public record OHLCV(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {

    public OHLCV {
        open = requireNonNegative(open, "open");
        high = requireNonNegative(high, "high");
        low = requireNonNegative(low, "low");
        close = requireNonNegative(close, "close");

        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high must be greater than or equal to low");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        BigDecimal normalized = Objects.requireNonNull(value, fieldName + " must not be null");
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return normalized;
    }
}
