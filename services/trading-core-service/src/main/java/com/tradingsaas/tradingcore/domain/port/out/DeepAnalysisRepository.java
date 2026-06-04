package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and reading the current deep-analysis artifact
 * of a signal. One current artifact per signal — {@link #save} replaces any
 * existing one (regenerate overwrites).
 */
public interface DeepAnalysisRepository {

    void save(UUID signalId, DeepAnalysisArtifact artifact);

    Optional<DeepAnalysisArtifact> findBySignalId(UUID signalId);
}
