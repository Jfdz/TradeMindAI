package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioJpaRepository extends JpaRepository<PortfolioJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"positions", "user"})
    Optional<PortfolioJpaEntity> findByUser_Id(UUID userId);
}
