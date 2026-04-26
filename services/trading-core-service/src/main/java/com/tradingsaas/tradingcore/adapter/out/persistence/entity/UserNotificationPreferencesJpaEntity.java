package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences", schema = "trading_core")
public class UserNotificationPreferencesJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpaEntity user;

    @Column(name = "signal_digest", nullable = false)
    private boolean signalDigest;

    @Column(name = "live_alerts", nullable = false)
    private boolean liveAlerts;

    @Column(name = "risk_warnings", nullable = false)
    private boolean riskWarnings;

    @Column(name = "strategy_changes", nullable = false)
    private boolean strategyChanges;

    @Column(name = "weekly_recap", nullable = false)
    private boolean weeklyRecap;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserNotificationPreferencesJpaEntity() {}

    public UserNotificationPreferencesJpaEntity(UUID userId, UserJpaEntity user, boolean signalDigest, boolean liveAlerts,
                                                boolean riskWarnings, boolean strategyChanges, boolean weeklyRecap,
                                                Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.user = user;
        this.signalDigest = signalDigest;
        this.liveAlerts = liveAlerts;
        this.riskWarnings = riskWarnings;
        this.strategyChanges = strategyChanges;
        this.weeklyRecap = weeklyRecap;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UserJpaEntity getUser() {
        return user;
    }

    public boolean isSignalDigest() {
        return signalDigest;
    }

    public boolean isLiveAlerts() {
        return liveAlerts;
    }

    public boolean isRiskWarnings() {
        return riskWarnings;
    }

    public boolean isStrategyChanges() {
        return strategyChanges;
    }

    public boolean isWeeklyRecap() {
        return weeklyRecap;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
