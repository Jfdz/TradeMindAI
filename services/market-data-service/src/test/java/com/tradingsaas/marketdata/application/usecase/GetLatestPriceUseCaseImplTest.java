package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetLatestPriceUseCaseImplTest {

    private final StockPriceCache cache = mock(StockPriceCache.class);
    private final StockPriceRepository repository = mock(StockPriceRepository.class);
    private final GetLatestPriceUseCaseImpl useCase = new GetLatestPriceUseCaseImpl(cache, repository);

    @Test
    void returnsCachedPriceWithoutHittingDb() {
        StockPrice cached = price("AAPL");
        when(cache.findLatest("AAPL", TimeFrame.DAILY)).thenReturn(Optional.of(cached));

        Optional<StockPrice> result = useCase.getLatestPrice("aapl", TimeFrame.DAILY);

        assertTrue(result.isPresent());
        assertEquals(cached, result.get());
        verify(cache).findLatest("AAPL", TimeFrame.DAILY);
        verify(repository, never()).findLatestByTicker("AAPL", TimeFrame.DAILY);
    }

    @Test
    void fallsThroughToDbAndPopulatesCacheOnCacheMiss() {
        StockPrice fromDb = price("AAPL");
        when(cache.findLatest("AAPL", TimeFrame.DAILY)).thenReturn(Optional.empty());
        when(repository.findLatestByTicker("AAPL", TimeFrame.DAILY)).thenReturn(Optional.of(fromDb));

        Optional<StockPrice> result = useCase.getLatestPrice("AAPL", TimeFrame.DAILY);

        assertTrue(result.isPresent());
        verify(cache).cacheLatest(fromDb);
    }

    @Test
    void returnsEmptyWhenNeitherCacheNorDbHasData() {
        when(cache.findLatest("UNKNOWN", TimeFrame.DAILY)).thenReturn(Optional.empty());
        when(repository.findLatestByTicker("UNKNOWN", TimeFrame.DAILY)).thenReturn(Optional.empty());

        Optional<StockPrice> result = useCase.getLatestPrice("UNKNOWN", TimeFrame.DAILY);

        assertTrue(result.isEmpty());
    }

    private static StockPrice price(String ticker) {
        Symbol symbol = new Symbol(ticker, "Apple Inc.", "NASDAQ");
        OHLCV ohlcv = new OHLCV(new BigDecimal("170"), new BigDecimal("175"),
                new BigDecimal("168"), new BigDecimal("172"), 1_000_000L);
        return new StockPrice(symbol, LocalDate.of(2026, 4, 16), TimeFrame.DAILY, ohlcv);
    }
}
