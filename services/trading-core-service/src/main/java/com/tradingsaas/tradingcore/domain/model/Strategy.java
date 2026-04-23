package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned trading strategy definition.
 */
public class Strategy {

    private final UUID id;
    private final UUID userId;
    private final String name;
    private final String description;
    private final RiskParameters riskParameters;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Strategy(UUID id,
                    UUID userId,
                    String name,
                    String description,
                    RiskParameters riskParameters,
                    boolean active,
                    Instant createdAt,
                    Instant updatedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.id = id;
        this.userId = userId;
        this.name = name.trim();
        this.description = description;
        this.riskParameters = Objects.requireNonNull(riskParameters, "riskParameters must not be null");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RiskParameters getRiskParameters() {
        return riskParameters;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Strategy that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Strategy{id=" + id + ", userId=" + userId + ", name='" + name + '\'' + ", active=" + active + ", riskParameters=" + riskParameters + '}';
    }
}
