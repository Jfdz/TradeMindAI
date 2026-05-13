package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.in.UpdateSignalReasoningUseCase;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateSignalReasoningUseCaseImpl implements UpdateSignalReasoningUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateSignalReasoningUseCaseImpl.class);

    private final TradingSignalRepository repository;

    public UpdateSignalReasoningUseCaseImpl(TradingSignalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Outcome execute(
            UUID signalId,
            String reasoning,
            ReasoningStatus status,
            Instant reasoningGeneratedAt,
            ReasoningArtifact artifact) {
        Objects.requireNonNull(signalId, "signalId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(artifact, "artifact must not be null");

        boolean updated = repository.updateReasoningArtifact(
                signalId, reasoning, status, reasoningGeneratedAt, artifact);
        if (!updated) {
            log.info(
                    "event=update_reasoning.signal_not_found signal_id={} outcome={}",
                    signalId,
                    artifact.outcome());
            return Outcome.SIGNAL_NOT_FOUND;
        }
        log.info(
                "event=update_reasoning.applied signal_id={} outcome={} provider={} retry={}",
                signalId,
                artifact.outcome(),
                artifact.provider(),
                artifact.retryCount());
        return Outcome.UPDATED;
    }
}
