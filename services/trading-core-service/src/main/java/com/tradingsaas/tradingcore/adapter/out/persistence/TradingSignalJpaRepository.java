package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradingSignalJpaRepository extends JpaRepository<TradingSignalJpaEntity, UUID> {

    Page<TradingSignalJpaEntity> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    Optional<TradingSignalJpaEntity> findTopByOrderByGeneratedAtDesc();

    @Modifying
    @Query("UPDATE TradingSignalJpaEntity e SET e.reasoning = :reasoning, "
            + "e.reasoningStatus = :status, e.reasoningGeneratedAt = :generatedAt "
            + "WHERE e.id = :id")
    void updateReasoning(@Param("id") UUID id,
                         @Param("reasoning") String reasoning,
                         @Param("status") ReasoningStatus status,
                         @Param("generatedAt") Instant generatedAt);
}
