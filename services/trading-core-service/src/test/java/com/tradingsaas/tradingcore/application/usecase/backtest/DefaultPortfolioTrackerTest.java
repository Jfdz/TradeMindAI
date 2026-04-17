package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestOrder;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.model.backtest.OrderSide;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultPortfolioTrackerTest {

    @Test
    void markToMarketShouldTrackEquityAndPnlAcrossBars() {
        DefaultPortfolioTracker tracker = new DefaultPortfolioTracker();
        DefaultSimulatedBroker broker = new DefaultSimulatedBroker(
                new BigDecimal("0.001"),
                new BigDecimal("0.01"),
                Clock.fixed(Instant.parse("2026-04-17T12:00:00Z"), ZoneOffset.UTC)
        );

        BacktestOrder buyOrder = new BacktestOrder(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("100"),
                Instant.parse("2026-04-17T11:59:00Z")
        );
        tracker.applyFill(broker.execute(buyOrder));

        PortfolioSnapshot afterFirstBar = tracker.markToMarket(
                "AAPL",
                new OhlcvBar(Instant.parse("2026-04-17T12:00:00Z"), 104, 106, 103, 105, 1_000)
        );

        assertEquals(new BigDecimal("-1001.100000"), afterFirstBar.cash());
        assertEquals(new BigDecimal("49.000000"), afterFirstBar.unrealizedPnl());
        assertEquals(new BigDecimal("48.900000"), afterFirstBar.equity());
        assertEquals(1, tracker.equityCurve().size());

        BacktestOrder sellOrder = new BacktestOrder(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "AAPL",
                OrderSide.SELL,
                5,
                new BigDecimal("110"),
                Instant.parse("2026-04-17T12:30:00Z")
        );
        tracker.applyFill(broker.execute(sellOrder));

        PortfolioSnapshot afterSecondBar = tracker.markToMarket(
                "AAPL",
                new OhlcvBar(Instant.parse("2026-04-17T12:31:00Z"), 109, 111, 108, 110, 900)
        );

        assertEquals(new BigDecimal("-451.700000"), afterSecondBar.cash());
        assertEquals(new BigDecimal("48.900000"), afterSecondBar.realizedPnl());
        assertEquals(new BigDecimal("49.500000"), afterSecondBar.unrealizedPnl());
        assertEquals(new BigDecimal("98.300000"), afterSecondBar.equity());
        assertEquals(2, tracker.equityCurve().size());
        assertTrue(afterSecondBar.positions().containsKey("AAPL"));
    }
}
