package com.tradingsaas.marketdata.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.config.MarketDataIngestionProperties;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledMarketDataIngestionJobTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 4, 16);
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 4, 15);

    @Test
    void runIngestsOnlyActiveSymbolsUsingPreviousDayRange() {
        FetchMarketDataUseCase fetchMarketDataUseCase = mock(FetchMarketDataUseCase.class);
        MarketDataIngestionProperties properties = new MarketDataIngestionProperties(
                "0 0 18 ? * MON-FRI",
                ZONE,
                List.of(
                        new MarketDataIngestionProperties.TrackedSymbol("AAPL", "Apple Inc.", "NASDAQ", true),
                        new MarketDataIngestionProperties.TrackedSymbol("TSLA", "Tesla, Inc.", "NASDAQ", false),
                        new MarketDataIngestionProperties.TrackedSymbol("MSFT", "Microsoft Corp.", "NASDAQ", true)));

        Clock clock = Clock.fixed(
                ZonedDateTime.of(2026, 4, 16, 18, 0, 0, 0, ZONE).toInstant(),
                ZONE);

        StockPrice stockPrice = new StockPrice(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                RUN_DATE,
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal("10.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("9.50"),
                        new BigDecimal("11.50"),
                        1_000L),
                new BigDecimal("11.40"));

        when(fetchMarketDataUseCase.fetchHistoricalData(
                        new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                        TimeFrame.DAILY,
                        FROM_DATE,
                        RUN_DATE))
                .thenReturn(List.of(stockPrice));
        when(fetchMarketDataUseCase.fetchHistoricalData(
                        new Symbol("MSFT", "Microsoft Corp.", "NASDAQ"),
                        TimeFrame.DAILY,
                        FROM_DATE,
                        RUN_DATE))
                .thenReturn(List.of());

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ScheduledMarketDataIngestionJob job = new ScheduledMarketDataIngestionJob(
                fetchMarketDataUseCase, properties, clock, meterRegistry);

        job.run();

        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                TimeFrame.DAILY,
                FROM_DATE,
                RUN_DATE);
        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("MSFT", "Microsoft Corp.", "NASDAQ"),
                TimeFrame.DAILY,
                FROM_DATE,
                RUN_DATE);
        verifyNoMoreInteractions(fetchMarketDataUseCase);
    }

    @Test
    void singleSymbolFailureDoesNotAbortRemainingSymbols() {
        FetchMarketDataUseCase fetchMarketDataUseCase = mock(FetchMarketDataUseCase.class);
        MarketDataIngestionProperties properties = new MarketDataIngestionProperties(
                "0 0 18 ? * MON-FRI",
                ZONE,
                List.of(
                        new MarketDataIngestionProperties.TrackedSymbol("AAPL", "Apple Inc.", "NASDAQ", true),
                        new MarketDataIngestionProperties.TrackedSymbol("MSFT", "Microsoft Corp.", "NASDAQ", true),
                        new MarketDataIngestionProperties.TrackedSymbol("GOOGL", "Alphabet Inc.", "NASDAQ", true)));

        Clock clock = Clock.fixed(
                ZonedDateTime.of(2026, 4, 16, 18, 0, 0, 0, ZONE).toInstant(),
                ZONE);

        when(fetchMarketDataUseCase.fetchHistoricalData(
                        eq(new Symbol("MSFT", "Microsoft Corp.", "NASDAQ")),
                        any(TimeFrame.class), any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new RuntimeException("yfinance throttled"));

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ScheduledMarketDataIngestionJob job = new ScheduledMarketDataIngestionJob(
                fetchMarketDataUseCase, properties, clock, meterRegistry);

        assertThatCode(job::run).doesNotThrowAnyException();

        // All 3 symbols attempted despite MSFT failure.
        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"), TimeFrame.DAILY, FROM_DATE, RUN_DATE);
        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("MSFT", "Microsoft Corp.", "NASDAQ"), TimeFrame.DAILY, FROM_DATE, RUN_DATE);
        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("GOOGL", "Alphabet Inc.", "NASDAQ"), TimeFrame.DAILY, FROM_DATE, RUN_DATE);
        verifyNoMoreInteractions(fetchMarketDataUseCase);

        // Metrics reflect 2 successes + 1 failure for last run.
        assertThat(meterRegistry.counter("marketdata.ingestion.runs.total",
                "outcome", "success", "symbol", "AAPL").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("marketdata.ingestion.runs.total",
                "outcome", "failure", "symbol", "MSFT").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("marketdata.ingestion.runs.total",
                "outcome", "success", "symbol", "GOOGL").count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("marketdata.ingestion.last_failed_count").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("marketdata.ingestion.last_success_timestamp_seconds").gauge().value())
                .isGreaterThan(0.0);
    }
}
