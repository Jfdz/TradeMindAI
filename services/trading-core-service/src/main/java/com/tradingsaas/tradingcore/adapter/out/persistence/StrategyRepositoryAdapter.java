package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.StrategyJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.mapper.StrategyEntityMapper;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.port.out.StrategyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class StrategyRepositoryAdapter implements StrategyRepository {

    private final StrategyJpaRepository strategyJpaRepository;
    private final StrategyEntityMapper mapper;

    StrategyRepositoryAdapter(StrategyJpaRepository strategyJpaRepository, StrategyEntityMapper mapper) {
        this.strategyJpaRepository = strategyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Strategy save(Strategy strategy) {
        StrategyJpaEntity saved = strategyJpaRepository.save(mapper.toEntity(strategy));
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Strategy> findAllByUserId(UUID userId, Pageable pageable) {
        return strategyJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Strategy> findByIdAndUserId(UUID id, UUID userId) {
        return strategyJpaRepository.findByIdAndUserId(id, userId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(Strategy strategy) {
        strategyJpaRepository.deleteByIdAndUserId(strategy.getId(), strategy.getUserId());
    }
}
