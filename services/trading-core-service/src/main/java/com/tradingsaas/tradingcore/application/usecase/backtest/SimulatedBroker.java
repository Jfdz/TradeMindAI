package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestOrder;
import com.tradingsaas.tradingcore.domain.model.backtest.ExecutionResult;

public interface SimulatedBroker {
    ExecutionResult execute(BacktestOrder order);
}
