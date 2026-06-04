package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import java.time.Instant;
import java.util.List;

/**
 * Combined payload returned by {@code GET /api/v1/internal/reasoning-context/{ticker}}.
 *
 * <p>{@code priceFacts} is non-null when the endpoint returns 200 — it is
 * the deterministic source of numeric truth for the downstream reasoning.
 * {@code news} can be empty without that being a failure; the entry in
 * {@code errors} explains which provider degraded so the caller can log
 * the partial outcome. {@code analystConsensus} is best-effort enrichment:
 * null when the provider returned nothing or failed, and never adds an
 * {@code errors} entry on a plain empty result.
 */
public record ReasoningContextResponse(
        String ticker,
        Instant generatedAt,
        PriceFactsResponse priceFacts,
        List<NewsItemResponse> news,
        AnalystConsensus analystConsensus,
        RecentPerformance recentPerformance,
        InsiderActivity insiderActivity,
        SocialSentiment socialSentiment,
        MacroContext macroContext,
        List<String> errors) {

    /**
     * Latest analyst-recommendation snapshot from the enrichment provider.
     *
     * <p>All counts are integers, so the downstream reasoning validator —
     * which only grounds decimal tokens — lets the LLM cite these verbatim
     * without an {@code ungrounded_number} violation. {@code period} is the
     * ISO date of the snapshot; {@code total} is the sum of the five buckets.
     */
    public record AnalystConsensus(
            String period,
            int strongBuy,
            int buy,
            int hold,
            int sell,
            int strongSell,
            int total) {}

    /**
     * The ticker's recent resolved win/loss track record — the deterministic
     * "reflection" the reasoning grounds in. Integer counts only (validator-safe,
     * like {@link AnalystConsensus}); null when there is no resolved history.
     * {@code resolvedCount} is {@code wins + losses}.
     */
    public record RecentPerformance(int wins, int losses, int resolvedCount) {}

    /**
     * Aggregated recent insider-transaction activity (Fase 4). Integer counts
     * only, so they are validator-safe to cite. {@code netShares} is the signed
     * sum of share changes; null when the provider had no coverage or failed.
     */
    public record InsiderActivity(int buyCount, int sellCount, long netShares) {}

    /**
     * Aggregated recent social-media sentiment (Fase 4). Integer MENTION counts
     * only — not decimal scores — so they are validator-safe to cite. Null when
     * the provider had no coverage or failed.
     */
    public record SocialSentiment(int positiveMentions, int negativeMentions, int totalMentions) {}

    /**
     * Coarse market-wide macro context (Fase 4). The same snapshot attaches to
     * every ticker. {@code recentMarketNewsCount} is the integer count of
     * general market-news items in the last 24h (validator-safe). Null when the
     * provider had no coverage or failed.
     */
    public record MacroContext(int recentMarketNewsCount) {}
}
