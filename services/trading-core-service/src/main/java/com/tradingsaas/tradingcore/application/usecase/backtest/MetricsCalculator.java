package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestMetrics;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.util.List;

public interface MetricsCalculator {
    BacktestMetrics calculate(List<EquityPoint> equityCurve, List<BacktestTrade> trades);
}
