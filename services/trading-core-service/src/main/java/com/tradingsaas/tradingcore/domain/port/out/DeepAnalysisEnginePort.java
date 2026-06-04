package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisSignalFacts;

/**
 * Outbound port to ai-engine's compute-only deep-analysis endpoint.
 *
 * <p>Implementations throw
 * {@link com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException}
 * when ai-engine cannot produce an artifact (no grounded facts, no verdict, or
 * transport failure). They never return a partial/sentinel object — a returned
 * artifact is always persistable.
 */
public interface DeepAnalysisEnginePort {

    DeepAnalysisArtifact generate(DeepAnalysisSignalFacts facts);
}
