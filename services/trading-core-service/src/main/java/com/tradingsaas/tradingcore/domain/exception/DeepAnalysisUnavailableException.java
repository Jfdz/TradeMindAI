package com.tradingsaas.tradingcore.domain.exception;

/**
 * Raised when ai-engine cannot produce a deep-analysis artifact (insufficient
 * grounded facts, no verdict, or transport failure). The controller maps this
 * to 503 — it is "analysis unavailable, retry later", not a server defect, and
 * nothing is persisted.
 */
public class DeepAnalysisUnavailableException extends RuntimeException {

    public DeepAnalysisUnavailableException(String message) {
        super(message);
    }
}
