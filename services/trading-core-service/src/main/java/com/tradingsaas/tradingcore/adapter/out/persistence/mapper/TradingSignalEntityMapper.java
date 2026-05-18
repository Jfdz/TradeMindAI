package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TradingSignalEntityMapper {

    public TradingSignalJpaEntity toEntity(TradingSignal signal) {
        TradingSignalJpaEntity entity = new TradingSignalJpaEntity(
                signal.getId(),
                signal.getSymbolId(),
                signal.getTicker(),
                signal.getType(),
                signal.getConfidence().getValue(),
                signal.getTimeframe(),
                signal.getGeneratedAt(),
                signal.getStopLossPct(),
                signal.getTakeProfitPct(),
                signal.getPredictedChangePct(),
                signal.getEntryPrice(),
                signal.getTargetPrice(),
                signal.getStopLoss(),
                signal.getExpectedMovePct(),
                signal.getReasoning(),
                signal.getReasoningStatus() != null ? signal.getReasoningStatus() : ReasoningStatus.PENDING,
                signal.getReasoningGeneratedAt());
        applyArtifact(entity, signal.getReasoningArtifact());
        return entity;
    }

    public TradingSignal toDomain(TradingSignalJpaEntity entity) {
        return new TradingSignal(
                entity.getId(),
                entity.getSymbolId(),
                entity.getTicker(),
                entity.getSignalType(),
                new Confidence(entity.getConfidence()),
                entity.getTimeframe(),
                entity.getGeneratedAt(),
                entity.getStopLossPct(),
                entity.getTakeProfitPct(),
                entity.getPredictedChangePct(),
                entity.getEntryPrice(),
                entity.getTargetPrice(),
                entity.getStopLoss(),
                entity.getExpectedMovePct(),
                entity.getReasoning(),
                entity.getReasoningStatus(),
                entity.getReasoningGeneratedAt(),
                extractArtifact(entity));
    }

    /**
     * Copies the artifact's audit fields into the entity's mutable
     * setters. Called by both the full {@link #toEntity} and the
     * {@code updateReasoningArtifact} repository path.
     */
    public void applyArtifact(TradingSignalJpaEntity entity, ReasoningArtifact artifact) {
        if (artifact == null) {
            entity.setReasoningOutcome(null);
            entity.setReasoningProvider(null);
            entity.setReasoningModelVersion(null);
            entity.setReasoningRetryCount(0);
            entity.setReasoningRefusalReason(null);
            entity.setReasoningFactsSnapshot(null);
            entity.setReasoningPriceRefs(null);
            entity.setReasoningNewsRefs(null);
            entity.setReasoningValidatorViolations(null);
            entity.setReasoningRawAudit(null);
            return;
        }
        entity.setReasoningOutcome(artifact.outcome());
        entity.setReasoningProvider(artifact.provider());
        entity.setReasoningModelVersion(artifact.modelVersion());
        entity.setReasoningRetryCount(artifact.retryCount());
        entity.setReasoningRefusalReason(artifact.refusalReason());
        entity.setReasoningFactsSnapshot(artifact.factsSnapshot());
        entity.setReasoningPriceRefs(artifact.priceRefs().isEmpty() ? null : artifact.priceRefs());
        entity.setReasoningNewsRefs(artifact.newsRefs().isEmpty() ? null : artifact.newsRefs());
        entity.setReasoningValidatorViolations(
                artifact.validatorViolations().isEmpty() ? null : artifact.validatorViolations());
        entity.setReasoningRawAudit(artifact.rawAudit());
    }

    private ReasoningArtifact extractArtifact(TradingSignalJpaEntity entity) {
        List<String> priceRefs = entity.getReasoningPriceRefs();
        List<String> newsRefs = entity.getReasoningNewsRefs();
        List<Map<String, Object>> violations = entity.getReasoningValidatorViolations();
        // retry_count excluded: column is NOT NULL DEFAULT 0, so every row would
        // qualify and the "no artifact at all" path would never fire.
        boolean anyField =
                entity.getReasoningOutcome() != null
             || entity.getReasoningProvider() != null
             || entity.getReasoningModelVersion() != null
             || entity.getReasoningRefusalReason() != null
             || entity.getReasoningFactsSnapshot() != null
             || (priceRefs != null && !priceRefs.isEmpty())
             || (newsRefs != null && !newsRefs.isEmpty())
             || (violations != null && !violations.isEmpty())
             || entity.getReasoningRawAudit() != null;
        if (!anyField) {
            return null;
        }
        return new ReasoningArtifact(
                entity.getReasoningOutcome(),
                entity.getReasoningProvider(),
                entity.getReasoningModelVersion(),
                entity.getReasoningRetryCount(),
                entity.getReasoningRefusalReason(),
                entity.getReasoningFactsSnapshot(),
                priceRefs,
                newsRefs,
                violations,
                entity.getReasoningRawAudit());
    }
}
