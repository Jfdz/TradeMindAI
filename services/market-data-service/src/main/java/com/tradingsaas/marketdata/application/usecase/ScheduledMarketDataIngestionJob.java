package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.config.MarketDataIngestionProperties;
import com.tradingsaas.marketdata.config.MarketDataIngestionProperties.TrackedSymbol;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledMarketDataIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMarketDataIngestionJob.class);

    private final FetchMarketDataUseCase fetchMarketDataUseCase;
    private final MarketDataIngestionProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final AtomicLong lastSuccessTimestampSeconds = new AtomicLong(0L);
    private final AtomicLong lastFailedCount = new AtomicLong(0L);

    public ScheduledMarketDataIngestionJob(
            FetchMarketDataUseCase fetchMarketDataUseCase,
            MarketDataIngestionProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.fetchMarketDataUseCase = Objects.requireNonNull(fetchMarketDataUseCase, "fetchMarketDataUseCase must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        meterRegistry.gauge("marketdata.ingestion.last_success_timestamp_seconds", lastSuccessTimestampSeconds);
        meterRegistry.gauge("marketdata.ingestion.last_failed_count", lastFailedCount);
    }

    @Scheduled(cron = "${market-data.ingestion.weekday-cron:0 0 18 ? * MON-FRI}", zone = "${market-data.ingestion.zone:America/New_York}")
    public void run() {
        LocalDate runDate = LocalDate.now(clock);
        LocalDate from = runDate.minusDays(1);

        int succeeded = 0;
        int failed = 0;

        for (TrackedSymbol trackedSymbol : properties.activeTrackedSymbols()) {
            String ticker = trackedSymbol.ticker();
            try {
                fetchMarketDataUseCase.fetchHistoricalData(
                        new Symbol(ticker, trackedSymbol.name(), trackedSymbol.exchange(), "", true),
                        TimeFrame.DAILY,
                        from,
                        runDate);
                succeeded++;
                ingestionCounter("success", ticker).increment();
            } catch (Exception ex) {
                failed++;
                ingestionCounter("failure", ticker).increment();
                log.warn("event=marketdata_ingestion_symbol_failed ticker={} cause=\"{}\"",
                        ticker, ex.getMessage(), ex);
            }
        }

        lastFailedCount.set(failed);
        lastSuccessTimestampSeconds.set(Instant.now(clock).getEpochSecond());

        log.info("event=marketdata_ingestion_completed succeeded={} failed={} run_date={}",
                succeeded, failed, runDate);
    }

    private Counter ingestionCounter(String outcome, String ticker) {
        return Counter.builder("marketdata.ingestion.runs.total")
                .tag("outcome", outcome)
                .tag("symbol", ticker)
                .register(meterRegistry);
    }
}
