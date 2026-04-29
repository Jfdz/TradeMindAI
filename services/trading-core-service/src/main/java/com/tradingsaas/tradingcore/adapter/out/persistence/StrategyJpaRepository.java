package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.StrategyJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface StrategyJpaRepository extends JpaRepository<StrategyJpaEntity, UUID> {

    Page<StrategyJpaEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<StrategyJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndActiveTrue(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
