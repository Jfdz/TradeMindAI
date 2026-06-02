package com.tradingsaas.tradingcore.domain.model;

/**
 * Resolved performance state of a BUY/SELL signal, decided by first-touch of
 * target_price vs stop_loss over the daily bars since generation.
 *
 * <p>HOLD signals never get a {@code SignalPerformance} row, so this enum has
 * no HOLD member — absence of a row is rendered as "n/a" by consumers.
 */
public enum SignalOutcome {
    /** Target price touched before stop loss. */
    WIN,
    /** Stop loss touched before target price. */
    LOSS,
    /** Neither target nor stop touched yet. */
    OPEN
}
