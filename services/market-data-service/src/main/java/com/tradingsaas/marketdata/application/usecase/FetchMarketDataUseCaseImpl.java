package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import com.tradingsaas.marketdata.domain.port.out.MarketDataProvider;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FetchMarketDataUseCaseImpl implements FetchMarketDataUseCase {

    private final MarketDataProvider marketDataProvider;
    private final StockPriceRepository stockPriceRepository;
    private final MarketDataEventPublisher marketDataEventPublisher;
    private final StockPriceCache stockPriceCache;

    public FetchMarketDataUseCaseImpl(
            MarketDataProvider marketDataProvider,
            StockPriceRepository stockPriceRepository,
            MarketDataEventPublisher marketDataEventPublisher,
            StockPriceCache stockPriceCache) {
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider, "marketDataProvider must not be null");
        this.stockPriceRepository = Objects.requireNonNull(stockPriceRepository, "stockPriceRepository must not be null");
        this.marketDataEventPublisher = Objects.requireNonNull(marketDataEventPublisher, "marketDataEventPublisher must not be null");
        this.stockPriceCache = Objects.requireNonNull(stockPriceCache, "stockPriceCache must not be null");
    }

    @Override
    public List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to) {
        List<StockPrice> fetchedPrices = marketDataProvider.fetchHistoricalData(symbol, timeFrame, from, to);
        List<StockPrice> savedPrices = stockPriceRepository.saveAll(fetchedPrices);
        cacheLatestPrice(savedPrices);
        marketDataEventPublisher.publishPricesUpdated(symbol, timeFrame, from, to, savedPrices.size());
        return savedPrices;
    }

    private void cacheLatestPrice(List<StockPrice> prices) {
        if (prices.isEmpty()) {
            return;
        }
        prices.stream()
                .max(Comparator.comparing(StockPrice::date))
                .ifPresent(stockPriceCache::cacheLatest);
    }
}
