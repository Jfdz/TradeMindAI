package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
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
    private final BigDecimal entryPrice;
    private final BigDecimal targetPrice;
    private final BigDecimal stopLoss;
    private final BigDecimal expectedMovePct;
    private final String reasoning;
    private final ReasoningStatus reasoningStatus;
    private final Instant reasoningGeneratedAt;
    private final ReasoningArtifact reasoningArtifact;

    public TradingSignal(UUID id,
                         UUID symbolId,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt) {
        this(id, symbolId, null, type, confidence, timeframe, generatedAt, null, null, null, null, null, null, null, null, null, null, null);
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
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null, null, null, null, null, null, null, null, null);
    }

    public TradingSignal(UUID id,
                         UUID symbolId,
                         SignalType type,
                         Confidence confidence,
                         Timeframe timeframe,
                         Instant generatedAt,
                         BigDecimal stopLossPct,
                         BigDecimal takeProfitPct) {
        this(id, symbolId, null, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null, null, null, null, null, null, null, null, null);
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
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, predictedChangePct, null, null, null, null, null, null, null, null);
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
                         BigDecimal predictedChangePct,
                         BigDecimal entryPrice) {
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, predictedChangePct, entryPrice, null, null, null, null, null, null, null);
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
                         BigDecimal predictedChangePct,
                         BigDecimal entryPrice,
                         BigDecimal targetPrice,
                         BigDecimal stopLoss,
                         BigDecimal expectedMovePct) {
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct,
                predictedChangePct, entryPrice, targetPrice, stopLoss, expectedMovePct, null, null, null, null);
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
                         BigDecimal predictedChangePct,
                         BigDecimal entryPrice,
                         String reasoning,
                         ReasoningStatus reasoningStatus,
                         Instant reasoningGeneratedAt) {
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct,
                predictedChangePct, entryPrice, null, null, null, reasoning, reasoningStatus, reasoningGeneratedAt, null);
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
                         BigDecimal predictedChangePct,
                         BigDecimal entryPrice,
                         String reasoning,
                         ReasoningStatus reasoningStatus,
                         Instant reasoningGeneratedAt,
                         ReasoningArtifact reasoningArtifact) {
        this(id, symbolId, ticker, type, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct,
                predictedChangePct, entryPrice, null, null, null, reasoning, reasoningStatus, reasoningGeneratedAt, reasoningArtifact);
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
                         BigDecimal predictedChangePct,
                         BigDecimal entryPrice,
                         BigDecimal targetPrice,
                         BigDecimal stopLoss,
                         BigDecimal expectedMovePct,
                         String reasoning,
                         ReasoningStatus reasoningStatus,
                         Instant reasoningGeneratedAt,
                         ReasoningArtifact reasoningArtifact) {
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
        this.entryPrice = entryPrice;
        this.targetPrice = targetPrice;
        this.stopLoss = stopLoss;
        this.expectedMovePct = expectedMovePct;
        this.reasoning = reasoning;
        this.reasoningStatus = reasoningStatus;
        this.reasoningGeneratedAt = reasoningGeneratedAt;
        this.reasoningArtifact = reasoningArtifact;
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

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public BigDecimal getExpectedMovePct() {
        return expectedMovePct;
    }

    public String getReasoning() {
        return reasoning;
    }

    public ReasoningStatus getReasoningStatus() {
        return reasoningStatus;
    }

    public Instant getReasoningGeneratedAt() {
        return reasoningGeneratedAt;
    }

    public ReasoningArtifact getReasoningArtifact() {
        return reasoningArtifact;
    }

    /**
     * Returns a copy of this signal with the given reasoning artifact attached.
     * Used by the C6 internal endpoint after ai-engine emits the artifact.
     */
    public TradingSignal withReasoningArtifact(ReasoningArtifact artifact) {
        return new TradingSignal(
                id, symbolId, ticker, type, confidence, timeframe, generatedAt,
                stopLossPct, takeProfitPct, predictedChangePct, entryPrice,
                targetPrice, stopLoss, expectedMovePct,
                reasoning, reasoningStatus, reasoningGeneratedAt, artifact);
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
        return "TradingSignal{id=" + id + ", symbolId=" + symbolId + ", ticker=" + ticker + ", type=" + type + ", confidence=" + confidence + ", timeframe=" + timeframe + ", generatedAt=" + generatedAt + ", stopLossPct=" + stopLossPct + ", takeProfitPct=" + takeProfitPct + ", predictedChangePct=" + predictedChangePct + ", entryPrice=" + entryPrice + ", targetPrice=" + targetPrice + ", stopLoss=" + stopLoss + ", expectedMovePct=" + expectedMovePct + ", reasoningStatus=" + reasoningStatus + '}';
    }
}
