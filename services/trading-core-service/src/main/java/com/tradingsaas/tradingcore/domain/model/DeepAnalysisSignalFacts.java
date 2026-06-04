package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The deterministic signal facts ai-engine needs to ground a deep-analysis
 * debate. Assembled from a {@code trading_signals} row; the grounded market
 * context (price facts, news) is fetched by ai-engine itself from the ticker.
 */
public record DeepAnalysisSignalFacts(
        String ticker,
        String signalType,
        BigDecimal confidence,
        BigDecimal entryPrice,
        BigDecimal predictedChangePct,
        BigDecimal targetPrice,
        BigDecimal stopLoss,
        BigDecimal expectedMovePct,
        Instant generatedAt) {}
