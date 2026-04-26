package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Domain entity representing a generated trading signal.
 * No JPA or Spring annotations - pure domain model.
 */
public class TradingSignal {

    private final UUID id;
    private final UUID symbolId;
    private final String ticker;
    private final SignalType type;
    private final Confidence confidence;
    private final Timeframe timeframe;
    private final Instant generatedAt;
    private final BigDecimal stopLossPct;
    private final BigDecimal takeProfitPct;
    private final BigDecimal predictedChangePct;

    public TradingSignal(UUID id,
                         UUID symbolId,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt) {
        this(id, symbolId, null, type, confidence, timeframe, generatedAt, null, null);
    }

    public TradingSignal(UUID id,
                         UUID symbolId,
                         String ticker,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt,
                         BigDecimal stopLossPct,
                         BigDecimal takeProfitPct) {
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null);
    }

    public TradingSignal(UUID id,
                         UUID symbolId,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt,
                         BigDecimal stopLossPct,
                         BigDecimal takeProfitPct) {
        this(id, symbolId, null, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null);
    }

    public TradingSignal(UUID id,
                         UUID symbolId,
                         String ticker,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt,
                         BigDecimal stopLossPct,
                         BigDecimal takeProfitPct,
                         BigDecimal predictedChangePct) {
        Objects.requireNonNull(symbolId, "symbolId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        if (ticker != null && ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        if (stopLossPct != null && stopLossPct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("stopLossPct must not be negative");
        }
        if (takeProfitPct != null && takeProfitPct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("takeProfitPct must not be negative");
        }
        this.id = id;
        this.symbolId = symbolId;
        this.ticker = ticker == null || ticker.isBlank() ? null : ticker.toUpperCase();
        this.type = type;
        this.confidence = confidence;
        this.timeframe = timeframe;
        this.generatedAt = generatedAt;
        this.stopLossPct = stopLossPct;
        this.takeProfitPct = takeProfitPct;
        this.predictedChangePct = predictedChangePct;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSymbolId() {
        return symbolId;
    }

    public String getTicker() {
        return ticker;
    }

    public SignalType getType() {
        return type;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public BigDecimal getStopLossPct() {
        return stopLossPct;
    }

    public BigDecimal getTakeProfitPct() {
        return takeProfitPct;
    }

    public BigDecimal getPredictedChangePct() {
        return predictedChangePct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradingSignal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TradingSignal{id=" + id + ", symbolId=" + symbolId + ", ticker=" + ticker + ", type=" + type + ", confidence=" + confidence + ", timeframe=" + timeframe + ", generatedAt=" + generatedAt + ", stopLossPct=" + stopLossPct + ", takeProfitPct=" + takeProfitPct + ", predictedChangePct=" + predictedChangePct + '}';
    }
}
