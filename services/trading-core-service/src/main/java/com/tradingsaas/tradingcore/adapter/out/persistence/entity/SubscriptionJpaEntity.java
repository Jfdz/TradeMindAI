package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "trading_core")
public class SubscriptionJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserJpaEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private SubscriptionPlan plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected SubscriptionJpaEntity() {}

    public SubscriptionJpaEntity(UUID id, UserJpaEntity user, SubscriptionPlan plan,
                                  Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.plan = plan;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UserJpaEntity getUser() { return user; }
    public SubscriptionPlan getPlan() { return plan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
