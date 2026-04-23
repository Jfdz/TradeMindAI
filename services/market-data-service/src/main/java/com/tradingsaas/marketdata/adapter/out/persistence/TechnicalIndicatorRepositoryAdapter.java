package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.SymbolEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.TechnicalIndicatorEntity;
import com.tradingsaas.marketdata.adapter.out.persistence.mapper.TechnicalIndicatorEntityMapper;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.port.out.TechnicalIndicatorRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Component
public class TechnicalIndicatorRepositoryAdapter implements TechnicalIndicatorRepository {

    private final TechnicalIndicatorJpaRepository jpaRepository;
    private final TechnicalIndicatorEntityMapper mapper;

    public TechnicalIndicatorRepositoryAdapter(
            TechnicalIndicatorJpaRepository jpaRepository,
            TechnicalIndicatorEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public List<TechnicalIndicator> saveAll(List<TechnicalIndicator> indicators) {
        List<TechnicalIndicatorEntity> entities = indicators.stream()
                .map(mapper::toEntity)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicalIndicator> findBySymbolAndDate(Symbol symbol, LocalDate date) {
        return jpaRepository.findBySymbolTickerAndDate(symbol.ticker(), date).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicalIndicator> findBySymbolAndDateRange(Symbol symbol, LocalDate from, LocalDate to) {
        return jpaRepository.findBySymbolTickerAndDateBetween(symbol.ticker(), from, to).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TechnicalIndicator> findBySymbolAndDateAndType(
            Symbol symbol, LocalDate date, TechnicalIndicatorType type) {
        return jpaRepository
                .findBySymbolTickerAndDateAndType(symbol.ticker(), date, type)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicalIndicator> findLatestByTicker(String ticker, List<TechnicalIndicatorType> types) {
        List<TechnicalIndicatorEntity> entities = CollectionUtils.isEmpty(types)
                ? jpaRepository.findLatestByTicker(ticker)
                : jpaRepository.findLatestByTickerAndTypes(ticker, types);
        return entities.stream().map(mapper::toDomain).toList();
    }
}
