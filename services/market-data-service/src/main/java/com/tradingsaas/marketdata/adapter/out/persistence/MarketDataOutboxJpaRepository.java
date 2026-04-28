package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.MarketDataOutboxJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDataOutboxJpaRepository extends JpaRepository<MarketDataOutboxJpaEntity, Long> {

    List<MarketDataOutboxJpaEntity> findTop50ByPublishedAtIsNullAndAttemptCountLessThanOrderByCreatedAtAsc(int maxAttempts);
}
