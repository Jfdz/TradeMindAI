package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestMetrics;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultBacktestExecutionServiceTest {

    @Test
    void submitShouldAdvanceJobThroughAsyncStatuses() throws Exception {
        InMemoryBacktestJobStore jobStore = new InMemoryBacktestJobStore();
        CountDownLatch executionLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        BacktestProcessor processor = request -> {
            executionLatch.countDown();
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for test release", ex);
            }
            return new BacktestResult(
                    new BacktestMetrics(0, 0, 0, 0, 0, 0, 0, 0),
                    List.of(new BacktestTrade(request.symbol(), new BigDecimal("0"))),
                    new PortfolioSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, java.util.Map.of())
            );
        };

        Executor executor = command -> new Thread(command).start();
        DefaultBacktestExecutionService service = new DefaultBacktestExecutionService(
                jobStore,
                processor,
                executor,
                Clock.fixed(Instant.parse("2026-04-17T12:00:00Z"), ZoneOffset.UTC)
        );

        UUID jobId = service.submit(new BacktestRequest("AAPL", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), 10));
        BacktestJob pendingJob = jobStore.findById(jobId).orElseThrow();
        assertEquals(BacktestStatus.PENDING, pendingJob.status());

        assertTrue(executionLatch.await(5, TimeUnit.SECONDS));
        BacktestJob runningJob = jobStore.findById(jobId).orElseThrow();
        assertEquals(BacktestStatus.RUNNING, runningJob.status());

        releaseLatch.countDown();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            BacktestJob job = jobStore.findById(jobId).orElseThrow();
            if (job.status() == BacktestStatus.COMPLETED) {
                assertTrue(job.result() != null);
                return;
            }
            Thread.sleep(25);
        }

        throw new AssertionError("Job did not complete in time");
    }
}
