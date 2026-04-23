package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestOrder;
import com.tradingsaas.tradingcore.domain.model.backtest.ExecutionResult;
import com.tradingsaas.tradingcore.domain.model.backtest.OrderSide;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultSimulatedBrokerTest {

    @Test
    void executeShouldApplyBuySlippageAndCommission() {
        DefaultSimulatedBroker broker = new DefaultSimulatedBroker(
                new BigDecimal("0.001"),
                new BigDecimal("0.01"),
                Clock.fixed(Instant.parse("2026-04-17T12:00:00Z"), ZoneOffset.UTC)
        );

        BacktestOrder order = new BacktestOrder(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "AAPL",
                OrderSide.BUY,
                100,
                new BigDecimal("178"),
                Instant.parse("2026-04-17T11:59:00Z")
        );

        ExecutionResult result = broker.execute(order);

        assertEquals(new BigDecimal("178.178000"), result.fillPrice());
        assertEquals(new BigDecimal("1.000000"), result.commission());
        assertEquals(new BigDecimal("-17818.800000"), result.cashImpact());
        assertEquals(new BigDecimal("0.178000"), result.slippageApplied());
    }

    @Test
    void executeShouldApplySellSlippageAndCommission() {
        DefaultSimulatedBroker broker = new DefaultSimulatedBroker(
                new BigDecimal("0.001"),
                new BigDecimal("0.01"),
                Clock.fixed(Instant.parse("2026-04-17T12:00:00Z"), ZoneOffset.UTC)
        );

        BacktestOrder order = new BacktestOrder(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "NVDA",
                OrderSide.SELL,
                20,
                new BigDecimal("846.2"),
                Instant.parse("2026-04-17T11:59:00Z")
        );

        ExecutionResult result = broker.execute(order);

        assertEquals(new BigDecimal("845.353800"), result.fillPrice());
        assertEquals(new BigDecimal("0.200000"), result.commission());
        assertEquals(new BigDecimal("16906.876000"), result.cashImpact());
        assertEquals(new BigDecimal("0.846200"), result.slippageApplied());
    }
}
