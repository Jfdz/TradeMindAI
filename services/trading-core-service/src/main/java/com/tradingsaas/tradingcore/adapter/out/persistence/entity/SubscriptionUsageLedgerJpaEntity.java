package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_usage_ledger", schema = "trading_core")
public class SubscriptionUsageLedgerJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subscription_plan", nullable = false, length = 20)
    private String subscriptionPlan;

    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SubscriptionUsageLedgerJpaEntity() {}

    public SubscriptionUsageLedgerJpaEntity(
            UUID id,
            UUID userId,
            String subscriptionPlan,
            String featureKey,
            String httpMethod,
            String requestPath,
            int responseStatus,
            String outcome,
            Instant occurredAt) {
        this.id = id;
        this.userId = userId;
        this.subscriptionPlan = subscriptionPlan;
        this.featureKey = featureKey;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.responseStatus = responseStatus;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
