package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.BacktestJobJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BacktestJobJpaRepository extends JpaRepository<BacktestJobJpaEntity, UUID> {}
