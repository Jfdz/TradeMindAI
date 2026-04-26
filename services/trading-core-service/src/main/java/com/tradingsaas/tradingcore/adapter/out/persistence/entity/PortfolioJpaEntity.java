package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "portfolios", schema = "trading_core")
public class PortfolioJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserJpaEntity user;

    @Column(name = "initial_capital", nullable = false, precision = 18, scale = 2)
    private BigDecimal initialCapital;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "portfolio", fetch = FetchType.LAZY)
    private Set<PortfolioPositionJpaEntity> positions = new LinkedHashSet<>();

    protected PortfolioJpaEntity() {}

    public PortfolioJpaEntity(UUID id, UserJpaEntity user, BigDecimal initialCapital, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.initialCapital = initialCapital;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UserJpaEntity getUser() {
        return user;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<PortfolioPositionJpaEntity> getPositions() {
        return positions;
    }
}
