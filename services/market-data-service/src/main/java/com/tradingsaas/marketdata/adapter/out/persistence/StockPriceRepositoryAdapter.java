package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.mapper.StockPriceEntityMapper;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.StockPriceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockPriceRepositoryAdapter implements StockPriceRepository {

    private final StockPriceJpaRepository jpaRepository;
    private final StockPriceEntityMapper mapper;

    public StockPriceRepositoryAdapter(StockPriceJpaRepository jpaRepository, StockPriceEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public List<StockPrice> saveAll(List<StockPrice> prices) {
        return jpaRepository.saveAll(prices.stream().map(mapper::toEntity).toList())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockPrice> findHistoricalData(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to) {
        return jpaRepository.findAllByTickerAndTimeFrame(symbol.ticker(), timeFrame, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockPrice> findHistoricalDataPaged(
            String ticker, TimeFrame timeFrame, LocalDate from, LocalDate to, Pageable pageable) {
        return jpaRepository.findHistoricalByTickerAndTimeFrame(ticker, timeFrame, from, to, pageable)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockPrice> findLatest(Symbol symbol, TimeFrame timeFrame) {
        return jpaRepository.findLatestByTickerAndTimeFrame(symbol.ticker(), timeFrame)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockPrice> findLatestByTicker(String ticker, TimeFrame timeFrame) {
        return jpaRepository.findLatestByTickerAndTimeFrame(ticker, timeFrame)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockPrice> findLatestByTickers(List<String> tickers, TimeFrame timeFrame) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findLatestByTickersAndTimeFrame(tickers, timeFrame)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
