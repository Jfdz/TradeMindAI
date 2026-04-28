package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetLatestPriceUseCase;
import com.tradingsaas.marketdata.domain.port.out.StockPriceCache;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Override
    public List<StockPrice> getLatestPrices(List<String> tickers, TimeFrame timeFrame) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        List<String> normalizedTickers = tickers.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(ticker -> !ticker.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
        LinkedHashMap<String, StockPrice> pricesByTicker = new LinkedHashMap<>();

        for (String ticker : normalizedTickers) {
            cache.findLatest(ticker, timeFrame).ifPresent(price -> pricesByTicker.put(ticker, price));
        }

        Set<String> missing = normalizedTickers.stream()
                .filter(ticker -> !pricesByTicker.containsKey(ticker))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            List<StockPrice> fromDb = repository.findLatestByTickers(List.copyOf(missing), timeFrame);
            fromDb.forEach(price -> {
                pricesByTicker.put(price.symbol().ticker(), price);
                cache.cacheLatest(price);
            });
        }

        return normalizedTickers.stream()
                .map(pricesByTicker::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
