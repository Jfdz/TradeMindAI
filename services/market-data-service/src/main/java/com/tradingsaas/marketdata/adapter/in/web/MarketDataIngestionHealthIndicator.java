package com.tradingsaas.marketdata.adapter.in.web;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports {@code marketDataIngestion} status under {@code /actuator/health}:
 * <ul>
 *   <li>UP — last run within {@link #STALE_THRESHOLD} and zero failed symbols</li>
 *   <li>OUT_OF_SERVICE — last run within {@link #STALE_THRESHOLD} but had failed symbols (degraded)</li>
 *   <li>DOWN — last run older than {@link #STALE_THRESHOLD} or scheduler never ran</li>
 * </ul>
 *
 * Reads the gauges registered by {@link com.tradingsaas.marketdata.application.usecase.ScheduledMarketDataIngestionJob}.
 */
@Component("marketDataIngestion")
public class MarketDataIngestionHealthIndicator implements HealthIndicator {

    static final Duration STALE_THRESHOLD = Duration.ofHours(36);

    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public MarketDataIngestionHealthIndicator(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Health health() {
        long lastSuccess = readGauge("marketdata.ingestion.last_success_timestamp_seconds");
        long lastFailed = readGauge("marketdata.ingestion.last_failed_count");
        long nowSeconds = Instant.now(clock).getEpochSecond();
        long ageSeconds = lastSuccess == 0L ? Long.MAX_VALUE : nowSeconds - lastSuccess;

        Health.Builder builder = Health.unknown()
                .withDetail("lastSuccessTimestampSeconds", lastSuccess)
                .withDetail("lastFailedCount", lastFailed)
                .withDetail("ageSeconds", ageSeconds == Long.MAX_VALUE ? -1 : ageSeconds);

        if (lastSuccess == 0L) {
            return builder.down().withDetail("reason", "scheduler has not run since startup").build();
        }
        if (ageSeconds > STALE_THRESHOLD.toSeconds()) {
            return builder.down().withDetail("reason", "ingestion stale (>36h since last run)").build();
        }
        if (lastFailed > 0L) {
            return builder.outOfService().withDetail("reason", "previous run had failed symbols").build();
        }
        return builder.up().build();
    }

    private long readGauge(String name) {
        Gauge gauge = Search.in(meterRegistry).name(name).gauge();
        if (gauge == null) {
            return 0L;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? 0L : (long) value;
    }
}
