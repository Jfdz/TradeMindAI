package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "strategies", schema = "trading_core")
public class StrategyJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "stop_loss_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal stopLossPct;

    @Column(name = "take_profit_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal takeProfitPct;

    @Column(name = "max_position_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPositionPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StrategyJpaEntity() {}

    public StrategyJpaEntity(UUID id, UUID userId, String name, String description, boolean active,
                             BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal maxPositionPct,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.active = active;
        this.stopLossPct = stopLossPct;
        this.takeProfitPct = takeProfitPct;
        this.maxPositionPct = maxPositionPct;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public BigDecimal getStopLossPct() { return stopLossPct; }
    public BigDecimal getTakeProfitPct() { return takeProfitPct; }
    public BigDecimal getMaxPositionPct() { return maxPositionPct; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
