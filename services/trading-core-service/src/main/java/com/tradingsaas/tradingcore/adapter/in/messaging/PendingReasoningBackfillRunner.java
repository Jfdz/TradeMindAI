package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.config.RabbitMQConfig;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
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

    private final TradingSignalJpaRepository jpaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int batchSize;
    private final Duration olderThan;

    public PendingReasoningBackfillRunner(
            TradingSignalJpaRepository jpaRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${trading-core.reasoning.backfill-on-boot:true}") boolean enabled,
            @Value("${trading-core.reasoning.backfill-batch-size:200}") int batchSize,
            @Value("${trading-core.reasoning.backfill-older-than:PT1H}") Duration olderThan) {
        this.jpaRepository = jpaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.olderThan = olderThan;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rePublishPendingOnBoot() {
        if (!enabled) {
            log.info("Pending-reasoning backfill disabled by configuration");
            return;
        }
        Instant cutoff = Instant.now().minus(olderThan);
        List<TradingSignalJpaEntity> pending = jpaRepository.findByReasoningStatusAndOlderThan(
                ReasoningStatus.PENDING, cutoff, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            log.info("No PENDING reasoning rows older than {} to re-queue", olderThan);
            return;
        }
        int published = 0;
        for (TradingSignalJpaEntity entity : pending) {
            if (publish(entity)) {
                published++;
            }
        }
        log.info("Re-queued {} of {} PENDING reasoning rows (older than {})",
                published, pending.size(), olderThan);
    }

    private boolean publish(TradingSignalJpaEntity entity) {
        try {
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

}
