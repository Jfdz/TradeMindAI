package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataProvider;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FetchMarketDataUseCaseImpl implements FetchMarketDataUseCase {

    private final MarketDataProvider marketDataProvider;
    private final StockPriceRepository stockPriceRepository;
    private final MarketDataOutboxService marketDataOutboxService;
    private final StockPriceCache stockPriceCache;

    public FetchMarketDataUseCaseImpl(
            MarketDataProvider marketDataProvider,
            StockPriceRepository stockPriceRepository,
            MarketDataOutboxService marketDataOutboxService,
            StockPriceCache stockPriceCache) {
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider, "marketDataProvider must not be null");
        this.stockPriceRepository = Objects.requireNonNull(stockPriceRepository, "stockPriceRepository must not be null");
        this.marketDataOutboxService = Objects.requireNonNull(marketDataOutboxService, "marketDataOutboxService must not be null");
        this.stockPriceCache = Objects.requireNonNull(stockPriceCache, "stockPriceCache must not be null");
    }

    @Override
    @Transactional
    public List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to) {
        List<StockPrice> fetchedPrices = marketDataProvider.fetchHistoricalData(symbol, timeFrame, from, to);
        List<StockPrice> savedPrices = stockPriceRepository.saveAll(fetchedPrices);
        marketDataOutboxService.enqueuePricesUpdated(symbol, timeFrame, from, to, savedPrices.size());
        cacheLatestPriceAfterCommit(savedPrices);
        return savedPrices;
    }

    private void cacheLatestPriceAfterCommit(List<StockPrice> prices) {
        if (prices.isEmpty()) {
            return;
        }
        prices.stream()
                .max(Comparator.comparing(StockPrice::date))
                .ifPresent(this::cacheAfterCommit);
    }

    private void cacheAfterCommit(StockPrice stockPrice) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stockPriceCache.cacheLatest(stockPrice);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stockPriceCache.cacheLatest(stockPrice);
            }
        });
    }
}
