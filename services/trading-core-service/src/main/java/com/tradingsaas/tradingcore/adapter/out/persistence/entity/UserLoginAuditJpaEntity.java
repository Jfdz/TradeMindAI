package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_login_audit", schema = "trading_core")
public class UserLoginAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "logged_in_at", nullable = false)
    private Instant loggedInAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "refresh_token_hash")
    private String refreshTokenHash;

    protected UserLoginAuditJpaEntity() {}

    public UserLoginAuditJpaEntity(UUID userId, Instant loggedInAt, String ipAddress,
                                   String userAgent, String refreshTokenHash) {
        this.userId = userId;
        this.loggedInAt = loggedInAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.refreshTokenHash = refreshTokenHash;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getLoggedInAt() { return loggedInAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
}
