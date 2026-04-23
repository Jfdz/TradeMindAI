package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
class DefaultBacktestExecutionService implements BacktestExecutionService {

    private final BacktestJobStore jobStore;
    private final BacktestProcessor backtestProcessor;
    private final Executor executor;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    DefaultBacktestExecutionService(BacktestJobStore jobStore, BacktestProcessor backtestProcessor) {
        this(jobStore, backtestProcessor, Executors.newCachedThreadPool(), Clock.systemUTC());
    }

    DefaultBacktestExecutionService(BacktestJobStore jobStore, BacktestProcessor backtestProcessor, Executor executor, Clock clock) {
        this.jobStore = jobStore;
        this.backtestProcessor = backtestProcessor;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public UUID submit(BacktestRequest request) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        BacktestJob job = new BacktestJob(jobId, request, BacktestStatus.PENDING, null, now, now, null);
        jobStore.save(job);

        executor.execute(() -> runJob(jobId, request));
        return jobId;
    }

    @Override
    public BacktestJob getJob(UUID jobId) {
        return jobStore.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Backtest job not found: " + jobId));
    }

    @Override
    public List<BacktestJob> listJobs() {
        return jobStore.findAll().stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private void runJob(UUID jobId, BacktestRequest request) {
        try {
            Instant runningAt = Instant.now(clock);
            jobStore.updateStatus(jobId, BacktestStatus.RUNNING, runningAt);
            var result = backtestProcessor.execute(request);
            jobStore.complete(jobId, result, Instant.now(clock));
        } catch (RuntimeException ex) {
            jobStore.fail(jobId, ex.getMessage(), Instant.now(clock));
            throw ex;
        }
    }
}
