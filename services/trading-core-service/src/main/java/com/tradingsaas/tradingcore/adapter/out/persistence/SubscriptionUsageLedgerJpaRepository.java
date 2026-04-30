package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SubscriptionUsageLedgerJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionUsageLedgerJpaRepository extends JpaRepository<SubscriptionUsageLedgerJpaEntity, UUID> {
}
