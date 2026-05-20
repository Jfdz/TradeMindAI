package com.tradingsaas.marketdata.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class MarketDataIngestionHealthIndicatorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-16T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Test
    void downWhenSchedulerNeverRan() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("marketdata.ingestion.last_success_timestamp_seconds", new AtomicLong(0L));
        registry.gauge("marketdata.ingestion.last_failed_count", new AtomicLong(0L));

        Health health = new MarketDataIngestionHealthIndicator(registry, CLOCK).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "scheduler has not run since startup");
    }

    @Test
    void upWhenRecentRunAndZeroFailures() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("marketdata.ingestion.last_success_timestamp_seconds",
                new AtomicLong(FIXED_NOW.minusSeconds(60).getEpochSecond()));
        registry.gauge("marketdata.ingestion.last_failed_count", new AtomicLong(0L));

        Health health = new MarketDataIngestionHealthIndicator(registry, CLOCK).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void outOfServiceWhenRecentRunButHadFailures() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("marketdata.ingestion.last_success_timestamp_seconds",
                new AtomicLong(FIXED_NOW.minusSeconds(60).getEpochSecond()));
        registry.gauge("marketdata.ingestion.last_failed_count", new AtomicLong(3L));

        Health health = new MarketDataIngestionHealthIndicator(registry, CLOCK).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("lastFailedCount", 3L);
    }

    @Test
    void downWhenRunIsOlderThanStaleThreshold() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("marketdata.ingestion.last_success_timestamp_seconds",
                new AtomicLong(FIXED_NOW.minusSeconds(40 * 3600).getEpochSecond()));
        registry.gauge("marketdata.ingestion.last_failed_count", new AtomicLong(0L));

        Health health = new MarketDataIngestionHealthIndicator(registry, CLOCK).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "ingestion stale (>36h since last run)");
    }
}
