package com.tradingsaas.tradingcore.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradingSignalDomainModelTest {

    @Test
    void confidenceNormalizesAndPreservesValue() {
        Confidence confidence = new Confidence(new BigDecimal("0.8500"));

        assertEquals(new BigDecimal("0.85"), confidence.getValue());
    }

    @Test
    void confidenceRejectsValuesOutsideInclusiveRange() {
        assertThrows(IllegalArgumentException.class, () -> new Confidence(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> new Confidence(new BigDecimal("1.01")));
    }

    @Test
    void tradingSignalStoresCoreFields() {
        UUID id = UUID.randomUUID();
        UUID symbolId = UUID.randomUUID();
        Confidence confidence = new Confidence(new BigDecimal("0.72"));
        Instant generatedAt = Instant.parse("2026-04-17T10:00:00Z");

        TradingSignal signal = new TradingSignal(
                id,
                symbolId,
                SignalType.BUY,
                confidence,
                Timeframe.HOUR_1,
                generatedAt);

        assertEquals(id, signal.getId());
        assertEquals(symbolId, signal.getSymbolId());
        assertEquals(SignalType.BUY, signal.getType());
        assertEquals(confidence, signal.getConfidence());
        assertEquals(Timeframe.HOUR_1, signal.getTimeframe());
        assertEquals(generatedAt, signal.getGeneratedAt());
    }

    @Test
    void tradingSignalEqualityUsesIdentifier() {
        UUID id = UUID.randomUUID();
        UUID symbolId = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-04-17T10:00:00Z");

        TradingSignal first = new TradingSignal(
                id,
                symbolId,
                SignalType.SELL,
                new Confidence(new BigDecimal("0.61")),
                Timeframe.DAILY,
                generatedAt);
        TradingSignal second = new TradingSignal(
                id,
                UUID.randomUUID(),
                SignalType.HOLD,
                new Confidence(new BigDecimal("0.22")),
                Timeframe.WEEKLY,
                generatedAt);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void tradingSignalRejectsMissingRequiredFields() {
        UUID symbolId = UUID.randomUUID();
        Confidence confidence = new Confidence(new BigDecimal("0.50"));
        Instant generatedAt = Instant.now();

        assertThrows(NullPointerException.class, () -> new TradingSignal(null, null, SignalType.BUY, confidence, Timeframe.DAILY, generatedAt));
        assertThrows(NullPointerException.class, () -> new TradingSignal(null, symbolId, null, confidence, Timeframe.DAILY, generatedAt));
        assertThrows(NullPointerException.class, () -> new TradingSignal(null, symbolId, SignalType.BUY, null, Timeframe.DAILY, generatedAt));
        assertThrows(NullPointerException.class, () -> new TradingSignal(null, symbolId, SignalType.BUY, confidence, null, generatedAt));
        assertThrows(NullPointerException.class, () -> new TradingSignal(null, symbolId, SignalType.BUY, confidence, Timeframe.DAILY, null));
    }

    @Test
    void confidenceTreatsEquivalentScalesAsEqual() {
        Confidence first = new Confidence(new BigDecimal("0.5"));
        Confidence second = new Confidence(new BigDecimal("0.50"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new Confidence(new BigDecimal("0.51")));
    }
}
