package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Service-to-service entry point for ai-engine to attach the
 * {@link ReasoningArtifact} produced by the C5 validated pipeline.
 *
 * <p>Implementations must be idempotent — the same artifact can be
 * delivered more than once due to retries on the ai-engine side.
 */
public interface UpdateSignalReasoningUseCase {

    /** Outcome of an artifact update — the controller maps each to a status. */
    enum Outcome {
        UPDATED,
        SIGNAL_NOT_FOUND,
    }

    Outcome execute(UUID signalId,
                    String reasoning,
                    ReasoningStatus status,
                    Instant reasoningGeneratedAt,
                    ReasoningArtifact artifact);
}
