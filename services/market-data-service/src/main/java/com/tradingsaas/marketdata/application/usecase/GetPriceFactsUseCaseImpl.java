package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.domain.exception.InsufficientHistoryException;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetPriceFactsUseCase;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import com.tradingsaas.marketdata.domain.port.out.SymbolRepository;
import com.tradingsaas.marketdata.domain.service.PriceFactsCalculator;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetPriceFactsUseCaseImpl implements GetPriceFactsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetPriceFactsUseCaseImpl.class);

    static final int LOOKBACK_DAYS = 400;
    static final int MIN_BARS_REQUIRED = PriceFactsCalculator.SMA_200_PERIOD;
    static final Duration CACHE_TTL = Duration.ofMinutes(15);
    static final String CACHE_KEY_PREFIX = "market-data:price-facts:";

    private final SymbolRepository symbolRepository;
    private final StockPriceRepository stockPriceRepository;
    private final EnrichmentCache cache;
    private final Clock clock;
    private final PriceFactsCalculator calculator;

    public GetPriceFactsUseCaseImpl(
            SymbolRepository symbolRepository,
            StockPriceRepository stockPriceRepository,
            EnrichmentCache cache,
            Clock clock) {
        this.symbolRepository = Objects.requireNonNull(symbolRepository, "symbolRepository must not be null");
        this.stockPriceRepository = Objects.requireNonNull(stockPriceRepository, "stockPriceRepository must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.calculator = new PriceFactsCalculator();
    }

    @Override
    public Optional<PriceFacts> getPriceFacts(String ticker, TimeFrame timeFrame) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(timeFrame, "timeFrame must not be null");
        if (timeFrame != TimeFrame.DAILY) {
            throw new IllegalArgumentException("Only DAILY timeframe is supported in v0.1.0");
        }
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        if (normalizedTicker.isEmpty()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }

        LocalDate today = LocalDate.now(clock);
        String cacheKey = CACHE_KEY_PREFIX + normalizedTicker + ":" + today;
        Optional<PriceFacts> cached = cache.get(cacheKey, PriceFacts.class);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Symbol> symbol = symbolRepository.findByTicker(normalizedTicker);
        if (symbol.isEmpty()) {
            return Optional.empty();
        }

        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        List<StockPrice> history = stockPriceRepository.findHistoricalData(symbol.get(), TimeFrame.DAILY, from, today);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        if (history.size() < MIN_BARS_REQUIRED) {
            LOG.info(
                    "Insufficient history for price-facts ticker={} barsAvailable={} barsRequired={}",
                    normalizedTicker,
                    history.size(),
                    MIN_BARS_REQUIRED);
            throw new InsufficientHistoryException(normalizedTicker, history.size(), MIN_BARS_REQUIRED);
        }

        PriceFacts facts = calculator.calculate(normalizedTicker, history);
        cache.put(cacheKey, facts, CACHE_TTL);
        return Optional.of(facts);
    }
}
