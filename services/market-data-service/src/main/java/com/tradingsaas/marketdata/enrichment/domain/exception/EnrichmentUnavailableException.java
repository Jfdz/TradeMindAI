package com.tradingsaas.marketdata.enrichment.domain.exception;

public class EnrichmentUnavailableException extends RuntimeException {

    private final String ticker;
    private final String reason;

    public EnrichmentUnavailableException(String ticker, String reason, Throwable cause) {
        super("Enrichment unavailable for " + ticker + ": " + reason, cause);
        this.ticker = ticker;
        this.reason = reason;
    }

    public String getTicker() { return ticker; }
    public String getReason() { return reason; }
}
