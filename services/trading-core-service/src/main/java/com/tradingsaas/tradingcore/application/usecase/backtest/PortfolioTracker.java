package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import com.tradingsaas.tradingcore.domain.model.backtest.ExecutionResult;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.util.List;

public interface PortfolioTracker {
    void applyFill(ExecutionResult executionResult);

    PortfolioSnapshot markToMarket(String symbol, OhlcvBar bar);

    PortfolioSnapshot snapshot();

    List<EquityPoint> equityCurve();
}
