package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryBacktestJobStore implements BacktestJobStore {

    private final ConcurrentMap<UUID, BacktestJob> jobs = new ConcurrentHashMap<>();

    @Override
    public BacktestJob save(BacktestJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public List<BacktestJob> findAll() {
        return List.copyOf(jobs.values());
    }

    @Override
    public Optional<BacktestJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public BacktestJob updateStatus(UUID id, BacktestStatus status, Instant updatedAt) {
        return jobs.computeIfPresent(id, (key, current) -> current.withStatus(status, updatedAt));
    }

    @Override
    public BacktestJob complete(UUID id, BacktestResult result, Instant updatedAt) {
        return jobs.computeIfPresent(id, (key, current) -> current.withResult(result, updatedAt));
    }

    @Override
    public BacktestJob fail(UUID id, String errorMessage, Instant updatedAt) {
        return jobs.computeIfPresent(id, (key, current) -> current.withFailure(errorMessage, updatedAt));
    }
}
