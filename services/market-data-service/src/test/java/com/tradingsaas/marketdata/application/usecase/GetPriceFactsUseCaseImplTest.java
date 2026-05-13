package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.exception.InsufficientHistoryException;
import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import com.tradingsaas.marketdata.domain.port.out.SymbolRepository;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetPriceFactsUseCaseImplTest {

    private static final Symbol SYMBOL = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 12);

    private final SymbolRepository symbolRepo = mock(SymbolRepository.class);
    private final StockPriceRepository priceRepo = mock(StockPriceRepository.class);
    private final EnrichmentCache cache = mock(EnrichmentCache.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private final GetPriceFactsUseCaseImpl useCase =
            new GetPriceFactsUseCaseImpl(symbolRepo, priceRepo, cache, clock);

    @Test
    void returnsEmptyWhenSymbolNotTracked() {
        when(cache.get(anyString(), eq(PriceFacts.class))).thenReturn(Optional.empty());
        when(symbolRepo.findByTicker("UNKNOWN")).thenReturn(Optional.empty());

        Optional<PriceFacts> result = useCase.getPriceFacts("unknown", TimeFrame.DAILY);

        assertTrue(result.isEmpty());
        verify(priceRepo, never()).findHistoricalData(any(), any(), any(), any());
    }

    @Test
    void returnsEmptyWhenNoHistory() {
        when(cache.get(anyString(), eq(PriceFacts.class))).thenReturn(Optional.empty());
        when(symbolRepo.findByTicker("AAPL")).thenReturn(Optional.of(SYMBOL));
        when(priceRepo.findHistoricalData(eq(SYMBOL), eq(TimeFrame.DAILY), any(), any()))
                .thenReturn(List.of());

        Optional<PriceFacts> result = useCase.getPriceFacts("AAPL", TimeFrame.DAILY);

        assertTrue(result.isEmpty());
    }

    @Test
    void throwsInsufficientHistoryWhenBarsBelowThreshold() {
        when(cache.get(anyString(), eq(PriceFacts.class))).thenReturn(Optional.empty());
        when(symbolRepo.findByTicker("AAPL")).thenReturn(Optional.of(SYMBOL));
        when(priceRepo.findHistoricalData(eq(SYMBOL), eq(TimeFrame.DAILY), any(), any()))
                .thenReturn(constantSeries(50));

        InsufficientHistoryException ex = assertThrows(
                InsufficientHistoryException.class,
                () -> useCase.getPriceFacts("AAPL", TimeFrame.DAILY));

        assertEquals("AAPL", ex.ticker());
        assertEquals(50, ex.barsAvailable());
        assertEquals(200, ex.barsRequired());
    }

    @Test
    void computesAndCachesWhenHistoryIsSufficient() {
        when(cache.get(anyString(), eq(PriceFacts.class))).thenReturn(Optional.empty());
        when(symbolRepo.findByTicker("AAPL")).thenReturn(Optional.of(SYMBOL));
        when(priceRepo.findHistoricalData(eq(SYMBOL), eq(TimeFrame.DAILY), any(), any()))
                .thenReturn(constantSeries(220));

        Optional<PriceFacts> result = useCase.getPriceFacts("aapl", TimeFrame.DAILY);

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().ticker());
        verify(cache, times(1)).put(
                eq("market-data:price-facts:AAPL:" + TODAY),
                eq(result.get()),
                eq(Duration.ofMinutes(15)));
    }

    @Test
    void servesFromCacheWhenPresent() {
        PriceFacts cached = new PriceFacts(
                "AAPL", TimeFrame.DAILY, TODAY, 220,
                new BigDecimal("100"), null, null, null, null, null, null,
                null, null, null, null, null, 0L, null, null, null);
        when(cache.get("market-data:price-facts:AAPL:" + TODAY, PriceFacts.class))
                .thenReturn(Optional.of(cached));

        Optional<PriceFacts> result = useCase.getPriceFacts("AAPL", TimeFrame.DAILY);

        assertTrue(result.isPresent());
        assertSame(cached, result.get());
        verify(symbolRepo, never()).findByTicker(any());
        verify(priceRepo, never()).findHistoricalData(any(), any(), any(), any());
    }

    @Test
    void rejectsNonDailyTimeFrame() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.getPriceFacts("AAPL", TimeFrame.HOUR_1));
    }

    @Test
    void rejectsBlankTicker() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.getPriceFacts("   ", TimeFrame.DAILY));
    }

    private static List<StockPrice> constantSeries(int days) {
        List<StockPrice> history = new ArrayList<>(days);
        LocalDate start = TODAY.minusDays(days);
        BigDecimal price = new BigDecimal("150.00");
        for (int i = 0; i < days; i++) {
            history.add(new StockPrice(
                    SYMBOL,
                    start.plusDays(i),
                    TimeFrame.DAILY,
                    new OHLCV(price, price, price, price, 1_000L)));
        }
        return history;
    }
}
