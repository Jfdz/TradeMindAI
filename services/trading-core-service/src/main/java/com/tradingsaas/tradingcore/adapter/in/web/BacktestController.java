package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.annotation.RequiresSubscription;
import com.tradingsaas.tradingcore.application.usecase.backtest.BacktestExecutionService;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestJob;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestRequest;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestResult;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioPosition;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {

    private final BacktestExecutionService backtestExecutionService;
    private final HistoricalMarketDataPort historicalMarketDataPort;

    BacktestController(BacktestExecutionService backtestExecutionService, HistoricalMarketDataPort historicalMarketDataPort) {
        this.backtestExecutionService = backtestExecutionService;
        this.historicalMarketDataPort = historicalMarketDataPort;
    }

    @GetMapping("/symbols/{symbol}/available")
    java.util.Map<String, Boolean> checkSymbolAvailability(@PathVariable String symbol) {
        return java.util.Map.of("available", historicalMarketDataPort.hasData(symbol));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresSubscription(SubscriptionPlan.BASIC)
    public BacktestJobResponse submitBacktest(@Valid @RequestBody BacktestSubmissionRequest request) {
        UUID jobId = backtestExecutionService.submit(request.toDomain());
        return BacktestJobResponse.from(backtestExecutionService.getJob(jobId));
    }

    @GetMapping
    List<BacktestJobResponse> listBacktests() {
        return backtestExecutionService.listJobs().stream().map(BacktestJobResponse::from).toList();
    }

    @GetMapping("/{id}")
    BacktestJobResponse getBacktest(@PathVariable UUID id) {
        return BacktestJobResponse.from(findJob(id));
    }

    @GetMapping("/{id}/trades")
    List<BacktestTradeResponse> getTrades(@PathVariable UUID id) {
        return BacktestJobResponse.from(findJob(id)).result().trades();
    }

    private BacktestJob findJob(UUID id) {
        try {
            return backtestExecutionService.getJob(id);
        } catch (IllegalArgumentException ex) {
            throw new BacktestNotFoundException(ex.getMessage());
        }
    }

    record BacktestSubmissionRequest(
            @NotBlank String symbol,
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            @Min(1) int quantity) {

        BacktestRequest toDomain() {
            return new BacktestRequest(symbol, from, to, quantity);
        }
    }

    record BacktestJobResponse(
            UUID id,
            String status,
            BacktestSubmissionRequest request,
            BacktestResultResponse result,
            Instant createdAt,
            Instant updatedAt,
            String errorMessage) {

        static BacktestJobResponse from(BacktestJob job) {
            return new BacktestJobResponse(
                    job.id(),
                    job.status().name(),
                    new BacktestSubmissionRequest(
                            job.request().symbol(),
                            job.request().from(),
                            job.request().to(),
                            job.request().quantity()
                    ),
                    job.result() == null ? null : BacktestResultResponse.from(job.result()),
                    job.createdAt(),
                    job.updatedAt(),
                    job.errorMessage()
            );
        }
    }

    record BacktestResultResponse(
            double totalReturn,
            double annualizedReturn,
            double sharpeRatio,
            double sortinoRatio,
            double maxDrawdown,
            double calmarRatio,
            double winRate,
            double profitFactor,
            List<BacktestTradeResponse> trades,
            PortfolioSnapshotResponse finalSnapshot) {

        static BacktestResultResponse from(BacktestResult result) {
            return new BacktestResultResponse(
                    result.metrics().totalReturn(),
                    result.metrics().annualizedReturn(),
                    result.metrics().sharpeRatio(),
                    result.metrics().sortinoRatio(),
                    result.metrics().maxDrawdown(),
                    result.metrics().calmarRatio(),
                    result.metrics().winRate(),
                    result.metrics().profitFactor(),
                    result.trades().stream().map(BacktestTradeResponse::from).toList(),
                    PortfolioSnapshotResponse.from(result.finalSnapshot())
            );
        }
    }

    record BacktestTradeResponse(String symbol, java.math.BigDecimal pnl) {
        static BacktestTradeResponse from(BacktestTrade trade) {
            return new BacktestTradeResponse(trade.symbol(), trade.pnl());
        }
    }

    record PortfolioSnapshotResponse(
            java.math.BigDecimal cash,
            java.math.BigDecimal realizedPnl,
            java.math.BigDecimal unrealizedPnl,
            java.math.BigDecimal equity,
            List<PortfolioPositionResponse> positions) {

        static PortfolioSnapshotResponse from(PortfolioSnapshot snapshot) {
            return new PortfolioSnapshotResponse(
                    snapshot.cash(),
                    snapshot.realizedPnl(),
                    snapshot.unrealizedPnl(),
                    snapshot.equity(),
                    snapshot.positions().values().stream().map(PortfolioPositionResponse::from).toList()
            );
        }
    }

    record PortfolioPositionResponse(String symbol, int quantity, java.math.BigDecimal averageCost, java.math.BigDecimal lastPrice) {
        static PortfolioPositionResponse from(PortfolioPosition position) {
            return new PortfolioPositionResponse(position.symbol(), position.quantity(), position.averageCost(), position.lastPrice());
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class BacktestNotFoundException extends RuntimeException {
        BacktestNotFoundException(String message) {
            super(message);
        }
    }
}
