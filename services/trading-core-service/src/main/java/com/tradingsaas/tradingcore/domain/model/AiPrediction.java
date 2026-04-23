package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Result returned by the AI engine for a ticker prediction.
 */
public class AiPrediction {

    private final String ticker;
    private final SignalType signalType;
    private final Confidence confidence;
    private final BigDecimal predictedChangePct;
    private final List<BigDecimal> rawLogits;
    private final Instant predictedAt;

    public AiPrediction(String ticker,
                        SignalType signalType,
                        Confidence confidence,
                        BigDecimal predictedChangePct,
                        List<BigDecimal> rawLogits,
                        Instant predictedAt) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        this.ticker = ticker.toUpperCase();
        this.signalType = Objects.requireNonNull(signalType, "signalType must not be null");
        this.confidence = Objects.requireNonNull(confidence, "confidence must not be null");
        this.predictedChangePct = Objects.requireNonNull(predictedChangePct, "predictedChangePct must not be null");
        this.rawLogits = List.copyOf(Objects.requireNonNull(rawLogits, "rawLogits must not be null"));
        this.predictedAt = Objects.requireNonNull(predictedAt, "predictedAt must not be null");
    }

    public static AiPrediction fallback(String ticker) {
        return new AiPrediction(
                ticker,
                SignalType.HOLD,
                new Confidence(BigDecimal.ZERO),
                BigDecimal.ZERO,
                List.of(),
                Instant.now());
    }

    public String getTicker() {
        return ticker;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public BigDecimal getPredictedChangePct() {
        return predictedChangePct;
    }

    public List<BigDecimal> getRawLogits() {
        return rawLogits;
    }

    public Instant getPredictedAt() {
        return predictedAt;
    }

    @Override
    public String toString() {
        return "AiPrediction{ticker='" + ticker + '\'' + ", signalType=" + signalType + ", confidence=" + confidence + ", predictedChangePct=" + predictedChangePct + ", rawLogits=" + rawLogits + ", predictedAt=" + predictedAt + '}';
    }
}
