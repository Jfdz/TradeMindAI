package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.adapter.out.persistence.SubscriptionUsageLedgerJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SubscriptionUsageLedgerJpaEntity;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionUsageLedgerService {

    private final SubscriptionUsageLedgerJpaRepository repository;

    public SubscriptionUsageLedgerService(SubscriptionUsageLedgerJpaRepository repository) {
        this.repository = repository;
    }

    public void record(TokenClaims claims, String featureKey, String httpMethod, String requestPath, int responseStatus) {
        repository.save(new SubscriptionUsageLedgerJpaEntity(
                UUID.randomUUID(),
                claims.userId(),
                normalizePlan(claims.subscriptionPlan()),
                featureKey,
                httpMethod,
                requestPath,
                responseStatus,
                responseStatus >= 400 ? "DENIED" : "ALLOWED",
                Instant.now()
        ));
    }

    private String normalizePlan(String subscriptionPlan) {
        return subscriptionPlan == null ? "FREE" : subscriptionPlan.toUpperCase(Locale.ROOT);
    }
}
