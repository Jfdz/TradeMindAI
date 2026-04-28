package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.application.usecase.backtest.BacktestExecutionService;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestMetrics;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestControllerTest {

    @Test
    void submitBacktestShouldReturnAcceptedJobSnapshot() {
        BacktestJob job = job();
        BacktestController controller = new BacktestController(new StubService(job), new StubMarketDataPort());

        BacktestController.BacktestJobResponse response = controller.submitBacktest(
                new BacktestController.BacktestSubmissionRequest("AAPL", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5), 10)
        );

        assertEquals(job.id(), response.id());
        assertEquals("COMPLETED", response.status());
        assertEquals(1, response.result().trades().size());
    }

    @Test
    void getTradesShouldExposeClosedTrades() {
        BacktestJob job = job();
        BacktestController controller = new BacktestController(new StubService(job), new StubMarketDataPort());

        List<BacktestController.BacktestTradeResponse> trades = controller.getTrades(job.id());

        assertEquals(1, trades.size());
        assertEquals("AAPL", trades.get(0).symbol());
    }

    @Test
    void throwsWhenBacktestMissing() {
        BacktestController controller = new BacktestController(new StubService(null), new StubMarketDataPort());
        assertThrows(BacktestController.BacktestNotFoundException.class, () -> controller.getBacktest(UUID.randomUUID()));
    }

    private static BacktestJob job() {
        UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
        BacktestRequest request = new BacktestRequest("AAPL", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5), 10);
        BacktestResult result = new BacktestResult(
                new BacktestMetrics(0.21, 0.24, 1.2, 1.1, -0.05, 4.8, 1.0, 2.1),
                List.of(new BacktestTrade("AAPL", new BigDecimal("48.900000"))),
                new PortfolioSnapshot(
                        new BigDecimal("-451.700000"),
                        new BigDecimal("48.900000"),
                        new BigDecimal("49.500000"),
                        new BigDecimal("98.300000"),
                        java.util.Map.of()
                )
        );
        return new BacktestJob(
                id,
                request,
                BacktestStatus.COMPLETED,
                result,
                Instant.parse("2026-04-17T12:00:00Z"),
                Instant.parse("2026-04-17T12:05:00Z"),
                null
        );
    }

    private static final class StubMarketDataPort implements HistoricalMarketDataPort {
        @Override
        public List<OhlcvBar> loadHistoricalBars(String symbol, java.time.LocalDate from, java.time.LocalDate to) {
            return List.of();
        }

        @Override
        public Map<String, BigDecimal> loadLatestPrices(List<String> symbols) {
            return Map.of();
        }

        @Override
        public boolean hasData(String symbol) {
            return true;
        }
    }

    private static final class StubService implements BacktestExecutionService {
        private final BacktestJob job;

        private StubService(BacktestJob job) {
            this.job = job;
        }

        @Override
        public UUID submit(com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest request) {
            return job.id();
        }

        @Override
        public BacktestJob getJob(UUID jobId) {
            if (job == null || !job.id().equals(jobId)) {
                throw new IllegalArgumentException("Backtest job not found: " + jobId);
            }
            return job;
        }

        @Override
        public List<BacktestJob> listJobs() {
            return job == null ? List.of() : List.of(job);
        }
    }
}
