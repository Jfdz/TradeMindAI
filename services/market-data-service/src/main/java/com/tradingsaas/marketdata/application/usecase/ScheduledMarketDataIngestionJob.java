package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.config.MarketDataIngestionProperties;
import com.tradingsaas.marketdata.config.MarketDataIngestionProperties.TrackedSymbol;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledMarketDataIngestionJob {

    private final FetchMarketDataUseCase fetchMarketDataUseCase;
    private final MarketDataIngestionProperties properties;
    private final Clock clock;

    public ScheduledMarketDataIngestionJob(
            FetchMarketDataUseCase fetchMarketDataUseCase,
            MarketDataIngestionProperties properties,
            Clock clock) {
        this.fetchMarketDataUseCase = Objects.requireNonNull(fetchMarketDataUseCase, "fetchMarketDataUseCase must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(cron = "${market-data.ingestion.weekday-cron:0 0 18 ? * MON-FRI}", zone = "${market-data.ingestion.zone:America/New_York}")
    public void run() {
        LocalDate runDate = LocalDate.now(clock);
        LocalDate from = runDate.minusDays(1);

        for (TrackedSymbol trackedSymbol : properties.activeTrackedSymbols()) {
            fetchMarketDataUseCase.fetchHistoricalData(
                    new Symbol(trackedSymbol.ticker(), trackedSymbol.name(), trackedSymbol.exchange(), "", true),
                    TimeFrame.DAILY,
                    from,
                    runDate);
        }
    }
}
