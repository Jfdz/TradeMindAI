package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;

public interface BacktestProcessor {
    BacktestResult execute(BacktestRequest request);
}
