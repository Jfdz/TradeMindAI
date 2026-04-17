package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TradingSignalJpaRepository extends JpaRepository<TradingSignalJpaEntity, UUID> {

    Page<TradingSignalJpaEntity> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    Optional<TradingSignalJpaEntity> findTopByOrderByGeneratedAtDesc();
}
