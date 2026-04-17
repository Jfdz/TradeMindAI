package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Strategy risk configuration.
 */
public class RiskParameters {

    private final BigDecimal stopLossPct;
    private final BigDecimal takeProfitPct;
    private final BigDecimal maxPositionPct;

    public RiskParameters(BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal maxPositionPct) {
        validateRange(stopLossPct, 0, 50, "stopLossPct");
        validateRange(takeProfitPct, 0, 100, "takeProfitPct");
        validateRange(maxPositionPct, 0, 100, "maxPositionPct");
        this.stopLossPct = stopLossPct.stripTrailingZeros();
        this.takeProfitPct = takeProfitPct.stripTrailingZeros();
        this.maxPositionPct = maxPositionPct.stripTrailingZeros();
    }

    public BigDecimal getStopLossPct() {
        return stopLossPct;
    }

    public BigDecimal getTakeProfitPct() {
        return takeProfitPct;
    }

    public BigDecimal getMaxPositionPct() {
        return maxPositionPct;
    }

    private static void validateRange(BigDecimal value, int minExclusive, int maxInclusive, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(BigDecimal.valueOf(minExclusive)) <= 0 || value.compareTo(BigDecimal.valueOf(maxInclusive)) > 0) {
            throw new IllegalArgumentException(fieldName + " must be in (" + minExclusive + ", " + maxInclusive + "]");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiskParameters that)) return false;
        return stopLossPct.compareTo(that.stopLossPct) == 0
                && takeProfitPct.compareTo(that.takeProfitPct) == 0
                && maxPositionPct.compareTo(that.maxPositionPct) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopLossPct.stripTrailingZeros(), takeProfitPct.stripTrailingZeros(), maxPositionPct.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "RiskParameters{stopLossPct=" + stopLossPct + ", takeProfitPct=" + takeProfitPct + ", maxPositionPct=" + maxPositionPct + '}';
    }
}
