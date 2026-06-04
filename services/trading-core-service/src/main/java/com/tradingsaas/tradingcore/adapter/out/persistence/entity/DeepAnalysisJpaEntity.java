package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence shape for a signal's current deep-analysis artifact. The full
 * multi-section artifact lives in {@code artifact} (JSONB); {@code outcome},
 * {@code verdictDirection} and {@code conviction} are promoted for querying.
 */
@Entity
@Table(name = "deep_analyses", schema = "trading_core")
public class DeepAnalysisJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "signal_id", nullable = false)
    private UUID signalId;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "verdict_direction", nullable = false, length = 10)
    private String verdictDirection;

    @Column(name = "conviction", nullable = false, length = 12)
    private String conviction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artifact", columnDefinition = "JSONB", nullable = false)
    private Map<String, Object> artifact;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeepAnalysisJpaEntity() {}

    public DeepAnalysisJpaEntity(
            UUID id,
            UUID signalId,
            String outcome,
            String verdictDirection,
            String conviction,
            Map<String, Object> artifact,
            Instant generatedAt,
            Instant createdAt) {
        this.id = id;
        this.signalId = signalId;
        this.outcome = outcome;
        this.verdictDirection = verdictDirection;
        this.conviction = conviction;
        this.artifact = artifact;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSignalId() {
        return signalId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getVerdictDirection() {
        return verdictDirection;
    }

    public String getConviction() {
        return conviction;
    }

    public Map<String, Object> getArtifact() {
        return artifact;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public void setVerdictDirection(String verdictDirection) {
        this.verdictDirection = verdictDirection;
    }

    public void setConviction(String conviction) {
        this.conviction = conviction;
    }

    public void setArtifact(Map<String, Object> artifact) {
        this.artifact = artifact;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
