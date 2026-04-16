package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetLatestPriceUseCase;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetLatestPriceUseCaseImpl implements GetLatestPriceUseCase {

    private final StockPriceCache cache;
    private final StockPriceRepository repository;

    public GetLatestPriceUseCaseImpl(StockPriceCache cache, StockPriceRepository repository) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<StockPrice> getLatestPrice(String ticker, TimeFrame timeFrame) {
        String normalizedTicker = ticker.toUpperCase();
        Optional<StockPrice> cached = cache.findLatest(normalizedTicker, timeFrame);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<StockPrice> fromDb = repository.findLatestByTicker(normalizedTicker, timeFrame);
        fromDb.ifPresent(cache::cacheLatest);
        return fromDb;
    }
}
