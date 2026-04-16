package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import com.tradingsaas.marketdata.domain.port.out.MarketDataProvider;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FetchMarketDataUseCaseImpl implements FetchMarketDataUseCase {

    private final MarketDataProvider marketDataProvider;
    private final StockPriceRepository stockPriceRepository;
    private final MarketDataEventPublisher marketDataEventPublisher;

    public FetchMarketDataUseCaseImpl(
            MarketDataProvider marketDataProvider,
            StockPriceRepository stockPriceRepository,
            MarketDataEventPublisher marketDataEventPublisher) {
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider, "marketDataProvider must not be null");
        this.stockPriceRepository = Objects.requireNonNull(stockPriceRepository, "stockPriceRepository must not be null");
        this.marketDataEventPublisher = Objects.requireNonNull(marketDataEventPublisher, "marketDataEventPublisher must not be null");
    }

    @Override
    public List<StockPrice> fetchHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to) {
        List<StockPrice> fetchedPrices = marketDataProvider.fetchHistoricalData(symbol, timeFrame, from, to);
        List<StockPrice> savedPrices = stockPriceRepository.saveAll(fetchedPrices);
        marketDataEventPublisher.publishPricesUpdated(symbol, timeFrame, from, to, savedPrices.size());
        return savedPrices;
    }
}
