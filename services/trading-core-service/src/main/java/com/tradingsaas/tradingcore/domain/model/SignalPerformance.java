package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Post-generation price behaviour of a single trading signal.
 *
 * <p>Prices are absolute (matching {@code trading_signals.entry_price NUMERIC(18,6)});
 * {@code maxProfit}/{@code maxDrawdown} are signed fractions (0.048 = +4.8%).
 * All price/return fields are nullable until the daily review job has enough
 * history to fill them. {@code resolvedAt} is set only when {@code outcome}
 * leaves {@link SignalOutcome#OPEN}.
 */
public record SignalPerformance(
        UUID signalId,
        String ticker,
        Instant generatedAt,
        BigDecimal entryPrice,
        BigDecimal price1d,
        BigDecimal price3d,
        BigDecimal price7d,
        BigDecimal price30d,
        BigDecimal maxProfit,
        BigDecimal maxDrawdown,
        SignalOutcome outcome,
        Instant resolvedAt,
        Instant evaluatedAt) {

    public SignalPerformance {
        if (signalId == null) {
            throw new IllegalArgumentException("signalId must not be null");
        }
        if (outcome == null) {
            outcome = SignalOutcome.OPEN;
        }
    }
}
