package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import java.util.List;
import java.util.UUID;

public interface BacktestExecutionService {
    UUID submit(BacktestRequest request);

    BacktestJob getJob(UUID jobId);

    List<BacktestJob> listJobs();
}
