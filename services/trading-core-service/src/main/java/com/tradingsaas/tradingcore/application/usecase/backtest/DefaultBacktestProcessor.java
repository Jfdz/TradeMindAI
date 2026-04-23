package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestOrder;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.model.backtest.OrderSide;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class DefaultBacktestProcessor implements BacktestProcessor {

    private final DataFeed dataFeed;
    private final SimulatedBroker simulatedBroker;
    private final MetricsCalculator metricsCalculator;

    DefaultBacktestProcessor(DataFeed dataFeed, SimulatedBroker simulatedBroker, MetricsCalculator metricsCalculator) {
        this.dataFeed = dataFeed;
        this.simulatedBroker = simulatedBroker;
        this.metricsCalculator = metricsCalculator;
    }

    @Override
    public BacktestResult execute(BacktestRequest request) {
        Iterator<OhlcvBar> feed = dataFeed.open(request.symbol(), request.from(), request.to());
        DefaultPortfolioTracker tracker = new DefaultPortfolioTracker();
        List<BacktestTrade> trades = new ArrayList<>();

        if (!feed.hasNext()) {
            PortfolioSnapshot snapshot = tracker.snapshot();
            return new BacktestResult(metricsCalculator.calculate(tracker.equityCurve(), trades), trades, snapshot);
        }

        OhlcvBar firstBar = feed.next();
        var buyOrder = new BacktestOrder(
                UUID.randomUUID(),
                request.symbol(),
                OrderSide.BUY,
                request.quantity(),
                BigDecimal.valueOf(firstBar.open()),
                firstBar.timestamp()
        );
        tracker.applyFill(simulatedBroker.execute(buyOrder));
        tracker.markToMarket(request.symbol(), firstBar);

        OhlcvBar lastBar = firstBar;
        while (feed.hasNext()) {
            lastBar = feed.next();
            tracker.markToMarket(request.symbol(), lastBar);
        }

        var sellOrder = new BacktestOrder(
                UUID.randomUUID(),
                request.symbol(),
                OrderSide.SELL,
                request.quantity(),
                BigDecimal.valueOf(lastBar.close()),
                lastBar.timestamp()
        );
        tracker.applyFill(simulatedBroker.execute(sellOrder));
        PortfolioSnapshot finalSnapshot = tracker.markToMarket(request.symbol(), lastBar);

        trades.add(new BacktestTrade(request.symbol(), finalSnapshot.realizedPnl()));

        return new BacktestResult(metricsCalculator.calculate(tracker.equityCurve(), trades), trades, finalSnapshot);
    }
}
