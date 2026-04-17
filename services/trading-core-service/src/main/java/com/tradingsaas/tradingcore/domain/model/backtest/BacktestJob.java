package com.tradingsaas.tradingcore.domain.model.backtest;

import java.time.Instant;
import java.util.UUID;

public record BacktestJob(
        UUID id,
        BacktestRequest request,
        BacktestStatus status,
        BacktestResult result,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage
) {
    public BacktestJob withStatus(BacktestStatus status, Instant updatedAt) {
        return new BacktestJob(id, request, status, result, createdAt, updatedAt, errorMessage);
    }

    public BacktestJob withResult(BacktestResult result, Instant updatedAt) {
        return new BacktestJob(id, request, BacktestStatus.COMPLETED, result, createdAt, updatedAt, null);
    }

    public BacktestJob withFailure(String errorMessage, Instant updatedAt) {
        return new BacktestJob(id, request, BacktestStatus.FAILED, result, createdAt, updatedAt, errorMessage);
    }
}
