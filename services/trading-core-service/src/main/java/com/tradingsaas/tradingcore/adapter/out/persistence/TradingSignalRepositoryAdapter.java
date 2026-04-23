package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.mapper.TradingSignalEntityMapper;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class TradingSignalRepositoryAdapter implements TradingSignalRepository {

    private final TradingSignalJpaRepository repository;
    private final TradingSignalEntityMapper mapper;

    TradingSignalRepositoryAdapter(TradingSignalJpaRepository repository, TradingSignalEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public TradingSignal save(TradingSignal signal) {
        TradingSignalJpaEntity saved = repository.save(mapper.toEntity(signal));
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TradingSignal> findAll(Pageable pageable) {
        return repository.findAllByOrderByGeneratedAtDesc(pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradingSignal> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TradingSignal> findLatest() {
        return repository.findTopByOrderByGeneratedAtDesc().map(mapper::toDomain);
    }
}
