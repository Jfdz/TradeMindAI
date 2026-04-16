package com.tradingsaas.marketdata.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import com.tradingsaas.marketdata.domain.port.out.MarketDataProvider;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FetchMarketDataUseCaseImplTest {

    @Test
    void fetchHistoricalDataFetchesStoresAndPublishesInOrder() {
        MarketDataProvider marketDataProvider = mock(MarketDataProvider.class);
        StockPriceRepository stockPriceRepository = mock(StockPriceRepository.class);
        MarketDataEventPublisher marketDataEventPublisher = mock(MarketDataEventPublisher.class);
        StockPriceCache stockPriceCache = mock(StockPriceCache.class);

        FetchMarketDataUseCaseImpl useCase = new FetchMarketDataUseCaseImpl(
                marketDataProvider, stockPriceRepository, marketDataEventPublisher, stockPriceCache);

        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        StockPrice stockPrice = new StockPrice(
                symbol,
                LocalDate.of(2026, 4, 16),
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal("10.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("9.50"),
                        new BigDecimal("11.50"),
                        1_000L),
                new BigDecimal("11.40"));

        when(marketDataProvider.fetchHistoricalData(symbol, TimeFrame.DAILY, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 16)))
                .thenReturn(List.of(stockPrice));
        when(stockPriceRepository.saveAll(List.of(stockPrice))).thenReturn(List.of(stockPrice));

        List<StockPrice> result = useCase.fetchHistoricalData(
                symbol, TimeFrame.DAILY, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 16));

        assertEquals(List.of(stockPrice), result);
        InOrder order = inOrder(marketDataProvider, stockPriceRepository, stockPriceCache, marketDataEventPublisher);
        order.verify(marketDataProvider)
                .fetchHistoricalData(symbol, TimeFrame.DAILY, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 16));
        order.verify(stockPriceRepository).saveAll(List.of(stockPrice));
        order.verify(stockPriceCache).cacheLatest(stockPrice);
        order.verify(marketDataEventPublisher)
                .publishPricesUpdated(symbol, TimeFrame.DAILY, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 16), 1);
        verifyNoMoreInteractions(marketDataProvider, stockPriceRepository, stockPriceCache, marketDataEventPublisher);
    }
}
