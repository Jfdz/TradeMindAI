package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetLatestPriceBatchUseCaseImplTest {

    private final StockPriceCache cache = mock(StockPriceCache.class);
    private final StockPriceRepository repository = mock(StockPriceRepository.class);
    private final GetLatestPriceUseCaseImpl useCase = new GetLatestPriceUseCaseImpl(cache, repository);

    @Test
    void returnsBatchPricesUsingCacheAndSingleRepositoryLookupForMisses() {
        StockPrice cached = price("AAPL");
        StockPrice fromDb = price("MSFT");
        when(cache.findLatest("AAPL", TimeFrame.DAILY)).thenReturn(Optional.of(cached));
        when(cache.findLatest("MSFT", TimeFrame.DAILY)).thenReturn(Optional.empty());
        when(repository.findLatestByTickers(List.of("MSFT"), TimeFrame.DAILY)).thenReturn(List.of(fromDb));

        List<StockPrice> result = useCase.getLatestPrices(List.of("aapl", "msft"), TimeFrame.DAILY);

        assertEquals(List.of(cached, fromDb), result);
        verify(repository).findLatestByTickers(List.of("MSFT"), TimeFrame.DAILY);
        verify(cache).cacheLatest(fromDb);
    }

    @Test
    void skipsRepositoryWhenAllPricesComeFromCache() {
        StockPrice cached = price("AAPL");
        when(cache.findLatest("AAPL", TimeFrame.DAILY)).thenReturn(Optional.of(cached));

        List<StockPrice> result = useCase.getLatestPrices(List.of("AAPL"), TimeFrame.DAILY);

        assertEquals(List.of(cached), result);
        verify(repository, never()).findLatestByTickers(List.of("AAPL"), TimeFrame.DAILY);
    }

    private static StockPrice price(String ticker) {
        return new StockPrice(
                new Symbol(ticker, ticker + " Inc.", "NASDAQ"),
                LocalDate.of(2026, 4, 28),
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal("170.00"),
                        new BigDecimal("175.00"),
                        new BigDecimal("168.00"),
                        new BigDecimal("172.00"),
                        1_000_000L
                )
        );
    }
}
