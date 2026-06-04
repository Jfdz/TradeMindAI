package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.DeepAnalysisJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeepAnalysisJpaRepository extends JpaRepository<DeepAnalysisJpaEntity, UUID> {

    Optional<DeepAnalysisJpaEntity> findBySignalId(UUID signalId);
}
