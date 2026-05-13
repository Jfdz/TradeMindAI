package com.tradingsaas.marketdata.domain.exception;

/**
 * Raised when there is not enough historical data to compute a snapshot
 * honestly. The use case must surface this rather than silently fall back
 * to zeroed indicators — {@code ai-engine} treats it as a refusal signal.
 */
public class InsufficientHistoryException extends RuntimeException {

    private final String ticker;
    private final int barsAvailable;
    private final int barsRequired;

    public InsufficientHistoryException(String ticker, int barsAvailable, int barsRequired) {
        super("Ticker " + ticker + " has " + barsAvailable + " bars; " + barsRequired + " required");
        this.ticker = ticker;
        this.barsAvailable = barsAvailable;
        this.barsRequired = barsRequired;
    }

    public String ticker() {
        return ticker;
    }

    public int barsAvailable() {
        return barsAvailable;
    }

    public int barsRequired() {
        return barsRequired;
    }
}
