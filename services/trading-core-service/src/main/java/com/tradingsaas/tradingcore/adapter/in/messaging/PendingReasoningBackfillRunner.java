package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.application.service.SignalMathService;
import com.tradingsaas.tradingcore.config.RabbitMQConfig;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Republishes reasoning-requested events for trading signals stuck in PENDING.
 *
 * Without this runner, rows that the V18 migration (or any other operation) sets to
 * {@code reasoning_status = 'PENDING'} stay blank forever, because the only producer
 * of reasoning events is {@code SignalGenerationService} firing on fresh signals.
 * This runner closes that gap by reconciling on every boot.
 */
@Component
public class PendingReasoningBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(PendingReasoningBackfillRunner.class);

    private static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_TAKE_PROFIT_PCT = new BigDecimal("4.00");

    // Hard cap on per-sweep work. Each sweep republishes at most this many rows
    // even when the backlog is larger; the next tick picks up the rest. Bounds
    // RabbitMQ pressure during recovery from a long outage.
    private static final int MAX_ROWS_PER_SWEEP = 5_000;

    private final TradingSignalJpaRepository jpaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final SignalMathService signalMath;
    private final Counter republishedCounter;
    private final Counter republishFailedCounter;
    private final boolean enabled;
    private final int batchSize;
    private final Duration olderThan;

    public PendingReasoningBackfillRunner(
            TradingSignalJpaRepository jpaRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            SignalMathService signalMath,
            MeterRegistry meterRegistry,
            @Value("${trading-core.reasoning.backfill-on-boot:true}") boolean enabled,
            @Value("${trading-core.reasoning.backfill-batch-size:200}") int batchSize,
            @Value("${trading-core.reasoning.backfill-older-than:PT1H}") Duration olderThan) {
        this.jpaRepository = jpaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.signalMath = signalMath;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.olderThan = olderThan;
        this.republishedCounter = Counter.builder("reasoning_backfill_republished_total")
                .description("Pending reasoning rows republished to the queue")
                .register(meterRegistry);
        this.republishFailedCounter = Counter.builder("reasoning_backfill_failed_total")
                .tags(Tags.of("reason", "publish_error"))
                .description("Pending reasoning rows that failed to republish")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rePublishPendingOnBoot() {
        if (!enabled) {
            log.info("Pending-reasoning backfill disabled by configuration");
            return;
        }
        runSweep("boot");
    }

    /**
     * Periodic reconciler. Catches PENDING rows that landed after boot
     * (e.g. SignalGenerationService publish failure during a RabbitMQ
     * blip) and any backlog the boot sweep capped on.
     *
     * Default cadence 5 min; override with
     * {@code trading-core.reasoning.backfill-cron} (Spring cron syntax).
     */
    @Scheduled(cron = "${trading-core.reasoning.backfill-cron:0 */5 * * * *}")
    public void rePublishPendingScheduled() {
        if (!enabled) {
            return;
        }
        runSweep("scheduled");
    }

    private void runSweep(String trigger) {
        Instant cutoff = Instant.now().minus(olderThan);
        int published = 0;
        int seen = 0;
        int pageIndex = 0;
        while (seen < MAX_ROWS_PER_SWEEP) {
            List<TradingSignalJpaEntity> pending = jpaRepository.findByReasoningStatusAndOlderThan(
                    ReasoningStatus.PENDING, cutoff, PageRequest.of(pageIndex, batchSize));
            if (pending.isEmpty()) {
                break;
            }
            seen += pending.size();
            for (TradingSignalJpaEntity entity : pending) {
                if (publish(entity)) {
                    published++;
                } else {
                    republishFailedCounter.increment();
                }
            }
            if (pending.size() < batchSize) {
                // Last page drained, no need to ask for another.
                break;
            }
            pageIndex++;
        }
        if (seen == 0) {
            log.debug("Pending-reasoning sweep ({}) found no rows older than {}", trigger, olderThan);
            return;
        }
        republishedCounter.increment(published);
        log.info("Pending-reasoning sweep ({}) re-queued {} of {} rows (cap={}, olderThan={})",
                trigger, published, seen, MAX_ROWS_PER_SWEEP, olderThan);
    }

    private boolean publish(TradingSignalJpaEntity entity) {
        try {
            healDerivedPricesIfMissing(entity);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("signalId", entity.getId().toString());
            event.put("ticker", entity.getTicker());
            event.put("signalType", entity.getSignalType() != null ? entity.getSignalType().name() : null);
            event.put("confidence", entity.getConfidence());
            event.put("predictedChangePct", entity.getPredictedChangePct());
            event.put("entryPrice", entity.getEntryPrice());
            event.put("targetPrice", entity.getTargetPrice());
            event.put("stopLoss", entity.getStopLoss());
            event.put("expectedMovePct", entity.getExpectedMovePct());
            // C9 — ai-engine consumer needs generated_at to build SignalInput.
            event.put("generatedAt",
                    entity.getGeneratedAt() != null ? entity.getGeneratedAt().toString() : null);
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(RabbitMQConfig.REASONING_QUEUE, payload);
            return true;
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize backfill payload for signal {}: {}", entity.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to publish backfill event for signal {}: {}", entity.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Pre-V21 rows have null target_price/stop_loss/expected_move_pct. Without
     * healing, requeued reasonings reach ai-engine with an empty grounded pool
     * for the new fields and the validator cannot enforce them. Compute on the
     * fly using stored entry_price + risk pcts and persist before publishing.
     */
    private void healDerivedPricesIfMissing(TradingSignalJpaEntity entity) {
        if (entity.getSignalType() == null || entity.getSignalType() == SignalType.HOLD) {
            return;
        }
        if (entity.getEntryPrice() == null) {
            return;
        }
        if (entity.getTargetPrice() != null
                && entity.getStopLoss() != null
                && entity.getExpectedMovePct() != null) {
            return;
        }
        BigDecimal slPct = entity.getStopLossPct() != null
                ? entity.getStopLossPct() : DEFAULT_STOP_LOSS_PCT;
        BigDecimal tpPct = entity.getTakeProfitPct() != null
                ? entity.getTakeProfitPct() : DEFAULT_TAKE_PROFIT_PCT;
        BigDecimal target = signalMath.calculateTargetPrice(
                entity.getSignalType(), entity.getEntryPrice(), tpPct);
        BigDecimal stop = signalMath.calculateStopLoss(
                entity.getSignalType(), entity.getEntryPrice(), slPct);
        BigDecimal move = signalMath.calculateExpectedMovePct(
                entity.getSignalType(), entity.getEntryPrice(), target);
        signalMath.validatePriceCoherence(
                entity.getSignalType(), entity.getEntryPrice(), target, stop);
        entity.setTargetPrice(target);
        entity.setStopLoss(stop);
        entity.setExpectedMovePct(move);
        jpaRepository.save(entity);
        log.info("backfill-runner: healed derived prices for signal id={} ticker={}",
                entity.getId(), entity.getTicker());
    }

}
