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
}
