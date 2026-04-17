package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.StrategyNotFoundException;
import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase;
import com.tradingsaas.tradingcore.domain.port.out.StrategyRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StrategyManagementService implements ManageStrategiesUseCase {

    private final StrategyRepository strategyRepository;

    StrategyManagementService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Strategy> getStrategies(UUID userId, Pageable pageable) {
        return strategyRepository.findAllByUserId(userId, pageable);
    }

    @Override
    @Transactional
    public Strategy createStrategy(UUID userId, StrategyCommand command) {
        Instant now = Instant.now();
        Strategy strategy = new Strategy(
                UUID.randomUUID(),
                userId,
                command.name(),
                command.description(),
                toRiskParameters(command),
                command.active(),
                now,
                now
        );
        return strategyRepository.save(strategy);
    }

    @Override
    @Transactional
    public Strategy updateStrategy(UUID userId, UUID strategyId, StrategyCommand command) {
        Strategy existing = strategyRepository.findByIdAndUserId(strategyId, userId)
                .orElseThrow(() -> new StrategyNotFoundException("Strategy not found: " + strategyId));

        Strategy updated = new Strategy(
                existing.getId(),
                existing.getUserId(),
                command.name(),
                command.description(),
                toRiskParameters(command),
                command.active(),
                existing.getCreatedAt(),
                Instant.now()
        );
        return strategyRepository.save(updated);
    }

    @Override
    @Transactional
    public void deleteStrategy(UUID userId, UUID strategyId) {
        Strategy existing = strategyRepository.findByIdAndUserId(strategyId, userId)
                .orElseThrow(() -> new StrategyNotFoundException("Strategy not found: " + strategyId));
        strategyRepository.delete(existing);
    }

    private RiskParameters toRiskParameters(StrategyCommand command) {
        return new RiskParameters(command.stopLossPct(), command.takeProfitPct(), command.maxPositionPct());
    }
}
