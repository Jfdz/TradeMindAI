package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioPositionJpaRepository extends JpaRepository<PortfolioPositionJpaEntity, UUID> {

    @Query("SELECT p FROM PortfolioPositionJpaEntity p WHERE p.id = :id AND p.portfolio.user.id = :userId")
    Optional<PortfolioPositionJpaEntity> findByIdAndUserId(UUID id, UUID userId);
}
