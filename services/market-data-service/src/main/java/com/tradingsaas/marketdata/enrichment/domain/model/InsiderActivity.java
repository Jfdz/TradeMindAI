package com.tradingsaas.marketdata.enrichment.domain.model;

import java.util.Objects;

/**
 * Aggregated recent insider-transaction activity for a ticker.
 *
 * <p>All fields are integer counts / share totals (never decimals), so the
 * downstream ai-engine reasoning validator — which only grounds decimal tokens
 * — lets the LLM cite them verbatim. {@code buyCount} / {@code sellCount} count
 * transactions whose share change was positive / negative; {@code netShares} is
 * the signed sum of share changes over the window the provider returned.
 */
public record InsiderActivity(String ticker, int buyCount, int sellCount, long netShares) {

    public InsiderActivity {
        Objects.requireNonNull(ticker, "ticker must not be null");
    }

    public boolean hasActivity() {
        return buyCount > 0 || sellCount > 0;
    }
}
