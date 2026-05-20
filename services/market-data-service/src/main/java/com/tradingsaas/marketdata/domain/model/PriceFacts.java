package com.tradingsaas.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Deterministic price + indicator snapshot for a ticker at a point in time.
 *
 * <p>Used by {@code ai-engine} as the authoritative source of numeric facts
 * when generating signal reasonings: values not present here must never
 * appear in generated text.
 *
 * <p>All numeric fields are nullable when there is not enough history to
 * compute them honestly (e.g. {@code sma200} requires 200 bars). Callers
 * must propagate nulls rather than substitute zeros.
 */
public record PriceFacts(
        String ticker,
        TimeFrame timeFrame,
        LocalDate snapshotAt,
        int barsAvailable,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal pctChange1d,
        BigDecimal pctChange5d,
        BigDecimal pctChange30d,
        BigDecimal high52w,
        BigDecimal low52w,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal sma200,
        BigDecimal rsi14,
        BigDecimal macdHistogram,
        long volume,
        BigDecimal volumeAvg20d,
        BigDecimal support,
        BigDecimal resistance) {

    public PriceFacts {
        ticker = Objects.requireNonNull(ticker, "ticker must not be null");
        timeFrame = Objects.requireNonNull(timeFrame, "timeFrame must not be null");
        snapshotAt = Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        close = Objects.requireNonNull(close, "close must not be null");
        if (barsAvailable < 1) {
            throw new IllegalArgumentException("barsAvailable must be at least 1");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }
}
