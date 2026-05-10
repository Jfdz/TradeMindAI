package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingsaas.tradingcore.domain.model.Confidence;
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
