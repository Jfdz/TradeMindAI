package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.DeepAnalysisJpaEntity;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps the {@link DeepAnalysisArtifact} domain record to and from the JSONB
 * {@code artifact} column via the application {@link ObjectMapper}. The promoted
 * scalar columns (outcome / verdict_direction / conviction) are derived on write
 * for querying; reads reconstruct the full artifact from the JSONB blob.
 */
@Component
public class DeepAnalysisRepositoryAdapter implements DeepAnalysisRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DeepAnalysisJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public DeepAnalysisRepositoryAdapter(
            DeepAnalysisJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UUID signalId, DeepAnalysisArtifact artifact) {
        Map<String, Object> blob = objectMapper.convertValue(artifact, MAP_TYPE);
        DeepAnalysisJpaEntity entity = jpaRepository.findBySignalId(signalId)
                .map(existing -> {
                    existing.setOutcome(artifact.outcome());
                    existing.setVerdictDirection(artifact.verdictDirection());
                    existing.setConviction(artifact.conviction());
                    existing.setArtifact(blob);
                    existing.setGeneratedAt(artifact.generatedAt());
                    return existing;
                })
                .orElseGet(() -> new DeepAnalysisJpaEntity(
                        UUID.randomUUID(),
                        signalId,
                        artifact.outcome(),
                        artifact.verdictDirection(),
                        artifact.conviction(),
                        blob,
                        artifact.generatedAt(),
                        Instant.now()));
        jpaRepository.save(entity);
    }

    @Override
    public Optional<DeepAnalysisArtifact> findBySignalId(UUID signalId) {
        return jpaRepository
                .findBySignalId(signalId)
                .map(entity -> objectMapper.convertValue(entity.getArtifact(), DeepAnalysisArtifact.class));
    }
}
