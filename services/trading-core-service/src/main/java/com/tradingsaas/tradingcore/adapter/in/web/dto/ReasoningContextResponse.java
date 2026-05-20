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
 * the partial outcome.
 */
public record ReasoningContextResponse(
        String ticker,
        Instant generatedAt,
        PriceFactsResponse priceFacts,
        List<NewsItemResponse> news,
        List<String> errors) {}
