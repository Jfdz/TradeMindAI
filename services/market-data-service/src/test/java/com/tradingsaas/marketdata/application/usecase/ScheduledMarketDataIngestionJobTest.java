package com.tradingsaas.marketdata.application.usecase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.config.MarketDataIngestionProperties;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledMarketDataIngestionJobTest {

    @Test
    void runIngestsOnlyActiveSymbolsUsingPreviousDayRange() {
        FetchMarketDataUseCase fetchMarketDataUseCase = mock(FetchMarketDataUseCase.class);
        MarketDataIngestionProperties properties = new MarketDataIngestionProperties(
                "0 0 18 ? * MON-FRI",
                ZoneId.of("America/New_York"),
                List.of(
                        new MarketDataIngestionProperties.TrackedSymbol("AAPL", "Apple Inc.", "NASDAQ", true),
                        new MarketDataIngestionProperties.TrackedSymbol("TSLA", "Tesla, Inc.", "NASDAQ", false),
                        new MarketDataIngestionProperties.TrackedSymbol("MSFT", "Microsoft Corp.", "NASDAQ", true)));

        Clock clock = Clock.fixed(
                ZonedDateTime.of(2026, 4, 16, 18, 0, 0, 0, ZoneId.of("America/New_York")).toInstant(),
                ZoneId.of("America/New_York"));

        StockPrice stockPrice = new StockPrice(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                java.time.LocalDate.of(2026, 4, 16),
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
                        java.time.LocalDate.of(2026, 4, 15),
                        java.time.LocalDate.of(2026, 4, 16)))
                .thenReturn(List.of(stockPrice));
        when(fetchMarketDataUseCase.fetchHistoricalData(
                        new Symbol("MSFT", "Microsoft Corp.", "NASDAQ"),
                        TimeFrame.DAILY,
                        java.time.LocalDate.of(2026, 4, 15),
                        java.time.LocalDate.of(2026, 4, 16)))
                .thenReturn(List.of());

        ScheduledMarketDataIngestionJob job = new ScheduledMarketDataIngestionJob(fetchMarketDataUseCase, properties, clock);

        job.run();

        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                TimeFrame.DAILY,
                java.time.LocalDate.of(2026, 4, 15),
                java.time.LocalDate.of(2026, 4, 16));
        verify(fetchMarketDataUseCase).fetchHistoricalData(
                new Symbol("MSFT", "Microsoft Corp.", "NASDAQ"),
                TimeFrame.DAILY,
                java.time.LocalDate.of(2026, 4, 15),
                java.time.LocalDate.of(2026, 4, 16));
        verifyNoMoreInteractions(fetchMarketDataUseCase);
    }
}
