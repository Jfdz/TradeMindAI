package com.tradingsaas.marketdata.enrichment.domain.model;

/**
 * Coarse market-wide macro context (Fase 4 enrichment). Not ticker-specific —
 * the same snapshot attaches to every reasoning context at a given time.
 *
 * <p>{@code recentMarketNewsCount} is the number of general-category market-news
 * items in the last 24h: a deliberately simple integer "macro news velocity"
 * proxy. Integer only → validator-safe, like {@link InsiderActivity}. Finnhub's
 * free tier has no cleaner ticker-relevant macro signal; richer macro (rates,
 * indices) would need a different provider and is a future enhancement.
 */
public record MacroContext(int recentMarketNewsCount) {

    public boolean hasContext() {
        return recentMarketNewsCount > 0;
    }
}
