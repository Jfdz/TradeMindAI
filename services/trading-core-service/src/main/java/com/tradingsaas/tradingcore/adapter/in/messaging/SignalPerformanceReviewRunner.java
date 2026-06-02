package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.application.service.SignalPerformanceEvaluator;
import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.SignalPerformanceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily reconciler that fills {@code signal_performance} from close-of-day prices.
 *
 * <p>Each sweep re-evaluates every tradeable (non-HOLD) signal generated within the
 * review window whose performance is still {@link SignalOutcome#OPEN} (or missing),
 * pulling daily bars from market-data and upserting the snapshot. Re-evaluating a
 * trailing window — not just yesterday — is what lets the longer-horizon columns
 * (price_30d) and late first-touch resolutions fill in over time. Once a signal
 * resolves to WIN/LOSS it is skipped on subsequent sweeps.
 */
@Component
public class SignalPerformanceReviewRunner {

    private static final Logger log = LoggerFactory.getLogger(SignalPerformanceReviewRunner.class);

    // Hard cap on per-sweep work; the next tick picks up any remainder.
    private static final int MAX_ROWS_PER_SWEEP = 5_000;

    private final TradingSignalJpaRepository signalRepository;
    private final SignalPerformanceRepository performanceRepository;
    private final SignalPerformanceEvaluator evaluator;
    private final HistoricalMarketDataPort marketDataPort;
    private final Clock clock;
    private final Counter resolvedWinCounter;
    private final Counter resolvedLossCounter;
    private final boolean enabled;
    private final int batchSize;
    private final Duration reviewWindow;

    public SignalPerformanceReviewRunner(
            TradingSignalJpaRepository signalRepository,
            SignalPerformanceRepository performanceRepository,
            SignalPerformanceEvaluator evaluator,
            HistoricalMarketDataPort marketDataPort,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${trading-core.performance.review-enabled:true}") boolean enabled,
            @Value("${trading-core.performance.review-batch-size:200}") int batchSize,
            @Value("${trading-core.performance.review-window:P90D}") Duration reviewWindow) {
        this.signalRepository = signalRepository;
        this.performanceRepository = performanceRepository;
        this.evaluator = evaluator;
        this.marketDataPort = marketDataPort;
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.reviewWindow = reviewWindow;
        this.resolvedWinCounter = Counter.builder("signal_performance_resolved_total")
                .tags(Tags.of("outcome", "win"))
                .description("Signals resolved to WIN by the daily performance review")
                .register(meterRegistry);
        this.resolvedLossCounter = Counter.builder("signal_performance_resolved_total")
                .tags(Tags.of("outcome", "loss"))
                .description("Signals resolved to LOSS by the daily performance review")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reviewOnBoot() {
        if (!enabled) {
            log.info("Signal-performance review disabled by configuration");
            return;
        }
        runSweep("boot");
    }

    /** Default 22:30 daily (after US market close); override with trading-core.performance.review-cron. */
    @Scheduled(cron = "${trading-core.performance.review-cron:0 30 22 * * *}")
    public void reviewScheduled() {
        if (!enabled) {
            return;
        }
        runSweep("scheduled");
    }

    private void runSweep(String trigger) {
        Instant cutoff = clock.instant().minus(reviewWindow);
        int evaluated = 0;
        int resolved = 0;
        int seen = 0;
        int pageIndex = 0;
        while (seen < MAX_ROWS_PER_SWEEP) {
            List<TradingSignalJpaEntity> candidates = signalRepository.findReviewCandidates(
                    cutoff, SignalType.HOLD, PageRequest.of(pageIndex, batchSize));
            if (candidates.isEmpty()) {
                break;
            }
            seen += candidates.size();

            Set<java.util.UUID> ids = candidates.stream()
                    .map(TradingSignalJpaEntity::getId)
                    .collect(Collectors.toSet());
            Map<java.util.UUID, SignalPerformance> existing = performanceRepository.findBySignalIds(ids);

            for (TradingSignalJpaEntity signal : candidates) {
                SignalPerformance prior = existing.get(signal.getId());
                if (prior != null && prior.outcome() != SignalOutcome.OPEN) {
                    continue; // already resolved — first-touch result is immutable
                }
                if (reviewOne(signal, prior)) {
                    resolved++;
                }
                evaluated++;
            }

            if (candidates.size() < batchSize) {
                break;
            }
            pageIndex++;
        }
        if (seen == 0) {
            log.debug("Signal-performance sweep ({}) found no candidates newer than {}", trigger, cutoff);
            return;
        }
        log.info("Signal-performance sweep ({}) evaluated {} of {} candidates, {} newly resolved (window={})",
                trigger, evaluated, seen, resolved, reviewWindow);
    }

    /** @return true if this evaluation newly resolved the signal (OPEN -> WIN/LOSS). */
    private boolean reviewOne(TradingSignalJpaEntity signal, SignalPerformance prior) {
        try {
            LocalDate from = signal.getGeneratedAt().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate to = LocalDate.now(clock);
            List<OhlcvBar> bars = marketDataPort.loadHistoricalBars(signal.getTicker(), from, to);

            SignalPerformance result = evaluator.evaluate(
                    signal.getId(),
                    signal.getTicker(),
                    signal.getSignalType(),
                    signal.getGeneratedAt(),
                    signal.getEntryPrice(),
                    signal.getTargetPrice(),
                    signal.getStopLoss(),
                    bars);

            performanceRepository.upsert(result);

            boolean newlyResolved = result.outcome() != SignalOutcome.OPEN
                    && (prior == null || prior.outcome() == SignalOutcome.OPEN);
            if (newlyResolved) {
                if (result.outcome() == SignalOutcome.WIN) {
                    resolvedWinCounter.increment();
                } else {
                    resolvedLossCounter.increment();
                }
                log.info("signal-performance: resolved signalId={} ticker={} outcome={} resolvedAt={}",
                        signal.getId(), signal.getTicker(), result.outcome(), result.resolvedAt());
            }
            return newlyResolved;
        } catch (Exception e) {
            // Fail safe: a market-data hiccup leaves the row OPEN for the next sweep.
            log.warn("signal-performance: failed to evaluate signalId={} ticker={}: {}",
                    signal.getId(), signal.getTicker(), e.getMessage());
            return false;
        }
    }
}
