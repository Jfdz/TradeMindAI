package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's subscription to a plan tier.
 * No JPA or Spring annotations — pure domain model.
 */
public class Subscription {

    private final UUID id;
    private final UUID userId;
    private final SubscriptionPlan plan;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Subscription(UUID id, UUID userId, SubscriptionPlan plan, Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(plan, "plan must not be null");
        this.id = id;
        this.userId = userId;
        this.plan = plan;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Subscription{id=" + id + ", userId=" + userId + ", plan=" + plan + '}';
    }
}
