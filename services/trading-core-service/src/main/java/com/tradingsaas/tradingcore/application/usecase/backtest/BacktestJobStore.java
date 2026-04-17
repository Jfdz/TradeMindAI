package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BacktestJobStore {
    BacktestJob save(BacktestJob job);

    List<BacktestJob> findAll();

    Optional<BacktestJob> findById(UUID id);

    BacktestJob updateStatus(UUID id, BacktestStatus status, Instant updatedAt);

    BacktestJob complete(UUID id, BacktestResult result, Instant updatedAt);

    BacktestJob fail(UUID id, String errorMessage, Instant updatedAt);
}
