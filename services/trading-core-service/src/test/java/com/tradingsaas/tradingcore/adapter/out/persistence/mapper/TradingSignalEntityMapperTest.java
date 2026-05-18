package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradingSignalEntityMapperTest {

    @Test
    void roundTripsDomainAndJpaModel() {
        TradingSignalEntityMapper mapper = new TradingSignalEntityMapper();
        TradingSignal original = new TradingSignal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                SignalType.SELL,
                new Confidence(new BigDecimal("0.61")),
                Timeframe.HOUR_1,
                Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"),
                new BigDecimal("4.00"));

        var entity = mapper.toEntity(original);
        var mapped = mapper.toDomain(entity);

        assertEquals(original, mapped);
        assertEquals(original.getStopLossPct(), mapped.getStopLossPct());
        assertEquals(original.getTakeProfitPct(), mapped.getTakeProfitPct());
        assertEquals(original.getEntryPrice(), mapped.getEntryPrice());
    }

    @Test
    void extractsPartialArtifactWhenOutcomeIsNullButProviderAndModelAreSet() {
        TradingSignalEntityMapper mapper = new TradingSignalEntityMapper();
        TradingSignalJpaEntity entity = new TradingSignalJpaEntity(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "TSLA",
                SignalType.BUY,
                new BigDecimal("0.5500"),
                Timeframe.DAILY,
                Instant.parse("2026-05-14T12:00:00Z"),
                null, null, null, null, null, null, null,
                null,
                ReasoningStatus.PENDING,
                null);
        entity.setReasoningProvider("anthropic_oauth");
        entity.setReasoningModelVersion("claude-haiku-4-5");

        TradingSignal mapped = mapper.toDomain(entity);

        assertNotNull(mapped.getReasoningArtifact(), "partial artifact must surface");
        assertNull(mapped.getReasoningArtifact().outcome());
        assertEquals("anthropic_oauth", mapped.getReasoningArtifact().provider());
        assertEquals("claude-haiku-4-5", mapped.getReasoningArtifact().modelVersion());
        assertEquals(0, mapped.getReasoningArtifact().retryCount());
    }

    @Test
    void returnsNullArtifactWhenAllArtifactColumnsAreEmpty() {
        TradingSignalEntityMapper mapper = new TradingSignalEntityMapper();
        TradingSignalJpaEntity entity = new TradingSignalJpaEntity(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "AAPL",
                SignalType.BUY,
                new BigDecimal("0.5500"),
                Timeframe.DAILY,
                Instant.parse("2026-05-14T12:00:00Z"),
                null, null, null, null, null, null, null,
                null,
                ReasoningStatus.PENDING,
                null);
        // retry_count defaults to 0 via NOT NULL; nothing else set.

        TradingSignal mapped = mapper.toDomain(entity);

        assertNull(mapped.getReasoningArtifact(),
                "genuine 'no artifact' state must still surface as null");
    }

    @Test
    void roundTripsEntryPrice() {
        TradingSignalEntityMapper mapper = new TradingSignalEntityMapper();
        TradingSignal original = new TradingSignal(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "AAPL",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.85")),
                Timeframe.DAILY,
                Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"),
                new BigDecimal("4.00"),
                new BigDecimal("1.50"),
                new BigDecimal("182.500000"));

        var entity = mapper.toEntity(original);
        var mapped = mapper.toDomain(entity);

        assertEquals(0, original.getEntryPrice().compareTo(mapped.getEntryPrice()));
    }
}
