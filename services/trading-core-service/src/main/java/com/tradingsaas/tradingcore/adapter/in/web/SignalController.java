package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningNewsSnapshot;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalPerformanceUseCase;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalsUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
class SignalController {

    private final GetSignalsUseCase getSignalsUseCase;
    private final GetSignalPerformanceUseCase getSignalPerformanceUseCase;

    SignalController(GetSignalsUseCase getSignalsUseCase,
                     GetSignalPerformanceUseCase getSignalPerformanceUseCase) {
        this.getSignalsUseCase = getSignalsUseCase;
        this.getSignalPerformanceUseCase = getSignalPerformanceUseCase;
    }

    @GetMapping
    Page<SignalResponse> listSignals(@PageableDefault(sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TradingSignal> page = getSignalsUseCase.getSignals(pageable);
        List<UUID> ids = page.getContent().stream().map(TradingSignal::getId).toList();
        Map<UUID, SignalPerformance> performance = getSignalPerformanceUseCase.findFor(ids);
        return page.map(signal -> SignalResponse.fromDomain(signal, performance.get(signal.getId())));
    }

    @GetMapping("/latest")
    SignalResponse getLatest() {
        return getSignalsUseCase.getLatest()
                .map(signal -> SignalResponse.fromDomain(
                        signal, getSignalPerformanceUseCase.findOne(signal.getId()).orElse(null)))
                .orElseThrow(() -> new SignalNotFoundException("No trading signals found"));
    }

    @GetMapping("/{id}")
    SignalResponse getById(@PathVariable UUID id) {
        return getSignalsUseCase.getById(id)
                .map(signal -> SignalResponse.fromDomain(
                        signal, getSignalPerformanceUseCase.findOne(id).orElse(null)))
                .orElseThrow(() -> new SignalNotFoundException("Signal not found: " + id));
    }

    @GetMapping("/performance/stats")
    List<PerformanceStatResponse> performanceStats() {
        return getSignalPerformanceUseCase.stats().stream()
                .map(PerformanceStatResponse::fromDomain)
                .collect(Collectors.toList());
    }

    record SignalResponse(
            UUID id,
            String symbol,
            String type,
            BigDecimal confidence,
            String timeframe,
            Instant generatedAt,
            BigDecimal stopLossPct,
            BigDecimal takeProfitPct,
            BigDecimal predictedChangePct,
            BigDecimal entryPrice,
            BigDecimal targetPrice,
            BigDecimal stopLoss,
            BigDecimal expectedMovePct,
            String reasoning,
            ReasoningStatus reasoningStatus,
            Instant reasoningGeneratedAt,
            ReasoningNewsSnapshot reasoningNews,
            String outcome,
            BigDecimal maxProfit,
            BigDecimal maxDrawdown,
            BigDecimal price30d) {

        static SignalResponse fromDomain(TradingSignal signal, SignalPerformance performance) {
            SignalOutcome outcome = performance != null ? performance.outcome() : null;
            return new SignalResponse(
                    signal.getId(),
                    signal.getTicker() != null ? signal.getTicker() : signal.getSymbolId().toString(),
                    signal.getType().name(),
                    signal.getConfidence().getValue(),
                    signal.getTimeframe().name(),
                    signal.getGeneratedAt(),
                    signal.getStopLossPct(),
                    signal.getTakeProfitPct(),
                    signal.getPredictedChangePct(),
                    signal.getEntryPrice(),
                    signal.getTargetPrice(),
                    signal.getStopLoss(),
                    signal.getExpectedMovePct(),
                    signal.getReasoning(),
                    signal.getReasoningStatus(),
                    signal.getReasoningGeneratedAt(),
                    ReasoningNewsSnapshot.fromArtifact(signal.getReasoningArtifact()),
                    outcome != null ? outcome.name() : null,
                    performance != null ? performance.maxProfit() : null,
                    performance != null ? performance.maxDrawdown() : null,
                    performance != null ? performance.price30d() : null);
        }
    }

    record PerformanceStatResponse(
            String signalType,
            String confidenceBand,
            long sampleSize,
            long wins,
            BigDecimal winRate,
            BigDecimal avgReturnPct,
            BigDecimal avgDrawdownPct) {

        static PerformanceStatResponse fromDomain(SignalPerformanceStat stat) {
            return new PerformanceStatResponse(
                    stat.signalType().name(),
                    stat.confidenceBand(),
                    stat.sampleSize(),
                    stat.wins(),
                    stat.winRate(),
                    stat.avgMaxProfit(),
                    stat.avgMaxDrawdown());
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class SignalNotFoundException extends RuntimeException {
        SignalNotFoundException(String message) {
            super(message);
        }
    }
}
