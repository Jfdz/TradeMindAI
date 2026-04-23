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
    }
}
