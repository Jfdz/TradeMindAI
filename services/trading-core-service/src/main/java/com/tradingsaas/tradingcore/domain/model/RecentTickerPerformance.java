package com.tradingsaas.tradingcore.domain.model;

/**
 * Recent resolved-signal track record for a single ticker — the deterministic
 * "reflection" the reasoning context carries forward so each new reasoning is
 * grounded in how recent same-ticker signals actually played out.
 *
 * <p>Only integer counts (no decimal returns), so the downstream reasoning
 * validator — which grounds only decimal tokens — lets the LLM cite them
 * verbatim without an {@code ungrounded_number} violation. {@code resolvedCount}
 * is {@code wins + losses} (OPEN signals are excluded).
 */
public record RecentTickerPerformance(int wins, int losses, int resolvedCount) {

    public RecentTickerPerformance {
        if (wins < 0 || losses < 0 || resolvedCount < 0) {
            throw new IllegalArgumentException("performance counts must be non-negative");
        }
    }

    /** True when there is at least one resolved signal to learn from. */
    public boolean hasHistory() {
        return resolvedCount > 0;
    }
}
