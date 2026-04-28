package com.tradingsaas.tradingcore.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.BacktestJobJpaEntity;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
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
import org.mockito.Mockito;

class BacktestJobStoreAdapterTest {

    private final BacktestJobJpaRepository repository = Mockito.mock(BacktestJobJpaRepository.class);
    private final BacktestJobStoreAdapter adapter = new BacktestJobStoreAdapter(repository, new ObjectMapper().findAndRegisterModules());

    @Test
    void saveShouldPersistAndRestoreJobPayload() {
        BacktestJob job = job(BacktestStatus.COMPLETED, result(), null);
        when(repository.save(Mockito.any(BacktestJobJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BacktestJob saved = adapter.save(job);

        assertEquals(job.id(), saved.id());
        assertEquals(job.request(), saved.request());
        assertEquals(job.status(), saved.status());
        assertEquals(job.result(), saved.result());
        assertNull(saved.errorMessage());
        verify(repository).save(Mockito.any(BacktestJobJpaEntity.class));
    }

    @Test
    void completeShouldStoreSerializedResult() {
        UUID jobId = UUID.randomUUID();
        BacktestJobJpaEntity pending = new BacktestJobJpaEntity(
                jobId,
                "AAPL",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                10,
                BacktestStatus.RUNNING,
                null,
                null,
                Instant.parse("2026-04-28T10:00:00Z"),
                Instant.parse("2026-04-28T10:00:00Z")
        );
        when(repository.findById(jobId)).thenReturn(Optional.of(pending));
        when(repository.save(Mockito.any(BacktestJobJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BacktestJob completed = adapter.complete(jobId, result(), Instant.parse("2026-04-28T10:05:00Z"));

        assertEquals(BacktestStatus.COMPLETED, completed.status());
        assertEquals(result(), completed.result());
        assertTrue(completed.updatedAt().isAfter(completed.createdAt()));
    }

    private static BacktestJob job(BacktestStatus status, BacktestResult result, String errorMessage) {
        Instant now = Instant.parse("2026-04-28T10:00:00Z");
        return new BacktestJob(
                UUID.randomUUID(),
                new BacktestRequest("AAPL", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), 10),
                status,
                result,
                now,
                now,
                errorMessage
        );
    }

    private static BacktestResult result() {
        return new BacktestResult(
                new BacktestMetrics(1.0, 0.5, 1.2, 0.9, -0.2, 0.8, 0.6, 1.5),
                List.of(new BacktestTrade("AAPL", new BigDecimal("12.34"))),
                new PortfolioSnapshot(
                        new BigDecimal("1000.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("2.00"),
                        new BigDecimal("1012.00"),
                        Map.of()
                )
        );
    }
}
