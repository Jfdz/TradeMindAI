package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.config.RabbitMQConfig;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PendingReasoningBackfillRunnerTest {

    private TradingSignalJpaRepository repo;
    private RabbitTemplate rabbit;
    private ObjectMapper objectMapper;

    @BeforeEach void setUp() {
        repo = mock(TradingSignalJpaRepository.class);
        rabbit = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();
    }

    private TradingSignalJpaEntity entity(String ticker, SignalType type, double confidence) {
        return new TradingSignalJpaEntity(
                UUID.randomUUID(), null, ticker, type, BigDecimal.valueOf(confidence),
                Timeframe.DAILY, Instant.now(),
                BigDecimal.valueOf(0.03), BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(0.02), BigDecimal.valueOf(100.0),
                null, ReasoningStatus.PENDING, null);
    }

    @Test void publishesOneEventPerPendingRow() throws Exception {
        when(repo.findByReasoningStatusAndOlderThan(
                eq(ReasoningStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(
                        entity("AAPL", SignalType.BUY, 0.65),
                        entity("TSLA", SignalType.SELL, 0.42),
                        entity("NVDA", SignalType.BUY, 0.78)));

        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 200, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbit, times(3))
                .convertAndSend(eq(RabbitMQConfig.REASONING_QUEUE), payloadCaptor.capture());
        List<String> payloads = payloadCaptor.getAllValues();
        assertThat(payloads).anyMatch(p -> p.contains("AAPL") && p.contains("BUY"));
        assertThat(payloads).anyMatch(p -> p.contains("TSLA") && p.contains("SELL"));
        assertThat(payloads).anyMatch(p -> p.contains("NVDA") && p.contains("BUY"));
        for (String p : payloads) {
            assertThat(p).contains("signalId").contains("confidence");
        }
    }

    @Test void doesNothingWhenNoRowsPending() {
        when(repo.findByReasoningStatusAndOlderThan(any(), any(), any()))
                .thenReturn(List.of());

        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 200, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        verifyNoInteractions(rabbit);
    }

    @Test void skipsEntirelyWhenDisabled() {
        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), false, 200, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        verifyNoInteractions(repo);
        verifyNoInteractions(rabbit);
    }

    @Test void continuesPublishingAfterTransientFailure() throws Exception {
        when(repo.findByReasoningStatusAndOlderThan(any(), any(), any()))
                .thenReturn(List.of(
                        entity("AAPL", SignalType.BUY, 0.65),
                        entity("TSLA", SignalType.BUY, 0.65),
                        entity("NVDA", SignalType.BUY, 0.65)));
        // Simulate Rabbit throwing on the second row only.
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .doNothing()
                .when(rabbit).convertAndSend(anyString(), anyString());

        // First call succeeds, second throws, third should still be attempted.
        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 200, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        verify(rabbit, times(3))
                .convertAndSend(eq(RabbitMQConfig.REASONING_QUEUE), anyString());
    }

    @Test void respectsBatchSize() {
        when(repo.findByReasoningStatusAndOlderThan(any(), any(), any()))
                .thenReturn(List.of(entity("AAPL", SignalType.BUY, 0.65)));

        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 50, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByReasoningStatusAndOlderThan(
                eq(ReasoningStatus.PENDING), any(Instant.class), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test void cutoffRespectsOlderThanDuration() {
        when(repo.findByReasoningStatusAndOlderThan(any(), any(), any()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 200, Duration.ofMinutes(30))
                .rePublishPendingOnBoot();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).findByReasoningStatusAndOlderThan(
                eq(ReasoningStatus.PENDING), cutoffCaptor.capture(), any());
        Instant cutoff = cutoffCaptor.getValue();
        // cutoff should be ~30 min before "now" (allowing for test latency)
        assertThat(cutoff).isBetween(before.minusSeconds(31 * 60), after.minusSeconds(29 * 60));
    }

    @Test void publishesValidJsonPayload() throws Exception {
        TradingSignalJpaEntity row = entity("AAPL", SignalType.BUY, 0.65);
        when(repo.findByReasoningStatusAndOlderThan(any(), any(), any()))
                .thenReturn(List.of(row));

        new PendingReasoningBackfillRunner(repo, rabbit, objectMapper, new com.tradingsaas.tradingcore.application.service.SignalMathService(), true, 200, Duration.ofHours(1))
                .rePublishPendingOnBoot();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.REASONING_QUEUE), payloadCaptor.capture());
        // Round-trip parse to confirm structure.
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> parsed = objectMapper.readValue(payloadCaptor.getValue(), java.util.Map.class);
        assertThat(parsed)
                .containsEntry("signalId", row.getId().toString())
                .containsEntry("ticker", "AAPL")
                .containsEntry("signalType", "BUY")
                .containsKey("confidence")
                // C9 — ai-engine consumer needs generatedAt to build SignalInput.
                .containsKey("generatedAt");
        assertThat(parsed.get("generatedAt"))
                .as("generatedAt must be ISO-8601 stringified Instant")
                .isInstanceOf(String.class);
    }
}
