package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Confidence score for a signal, constrained to the inclusive range [0, 1].
 */
public class Confidence {

    private final BigDecimal value;

    public Confidence(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1 inclusive");
        }
        this.value = value.stripTrailingZeros();
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Confidence that)) return false;
        return value.compareTo(that.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return "Confidence{value=" + value + '}';
    }
}
