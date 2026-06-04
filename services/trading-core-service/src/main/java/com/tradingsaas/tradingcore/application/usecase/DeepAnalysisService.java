package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.exception.SignalNotFoundException;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisSignalFacts;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisEnginePort;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the premium deep-analysis flow: load the signal facts, ask
 * ai-engine to run the grounded debate, persist the artifact, return it. The
 * tier gate lives on the controller; ai-engine owns the compute, this service
 * owns the data.
 *
 * <p>If ai-engine cannot produce an artifact it throws
 * {@link com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException};
 * nothing is persisted in that case (a non-deterministic debate must not leave
 * a half-written row).
 */
@Service
public class DeepAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeepAnalysisService.class);

    private final TradingSignalJpaRepository signalRepository;
    private final DeepAnalysisEnginePort engine;
    private final DeepAnalysisRepository repository;

    public DeepAnalysisService(
            TradingSignalJpaRepository signalRepository,
            DeepAnalysisEnginePort engine,
            DeepAnalysisRepository repository) {
        this.signalRepository = signalRepository;
        this.engine = engine;
        this.repository = repository;
    }

    @Transactional
    public DeepAnalysisArtifact generate(UUID signalId) {
        TradingSignalJpaEntity signal = signalRepository
                .findById(signalId)
                .orElseThrow(() -> new SignalNotFoundException(signalId));

        DeepAnalysisSignalFacts facts = new DeepAnalysisSignalFacts(
                signal.getTicker(),
                signal.getSignalType() == null ? "HOLD" : signal.getSignalType().name(),
                signal.getConfidence(),
                signal.getEntryPrice(),
                signal.getPredictedChangePct(),
                signal.getTargetPrice(),
                signal.getStopLoss(),
                signal.getExpectedMovePct(),
                signal.getGeneratedAt());

        DeepAnalysisArtifact artifact = engine.generate(facts);
        repository.save(signalId, artifact);
        log.info(
                "event=deep_analysis.persisted signal_id={} ticker={} verdict={} conviction={} outcome={}",
                signalId,
                facts.ticker(),
                artifact.verdictDirection(),
                artifact.conviction(),
                artifact.outcome());
        return artifact;
    }

    @Transactional(readOnly = true)
    public Optional<DeepAnalysisArtifact> get(UUID signalId) {
        return repository.findBySignalId(signalId);
    }
}
