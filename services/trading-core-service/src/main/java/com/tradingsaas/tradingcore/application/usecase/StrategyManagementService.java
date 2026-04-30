package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.exception.StrategyNotFoundException;
import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
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
    public Strategy createStrategy(UUID userId, String subscriptionPlan, StrategyCommand command) {
        enforceActiveStrategyLimit(userId, subscriptionPlan, command.active());
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
    public Strategy updateStrategy(UUID userId, String subscriptionPlan, UUID strategyId, StrategyCommand command) {
        Strategy existing = strategyRepository.findByIdAndUserId(strategyId, userId)
                .orElseThrow(() -> new StrategyNotFoundException("Strategy not found: " + strategyId));
        enforceActiveStrategyLimit(userId, subscriptionPlan, !existing.isActive() && command.active());

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

    private void enforceActiveStrategyLimit(UUID userId, String subscriptionPlan, boolean activatingStrategy) {
        if (!activatingStrategy) {
            return;
        }

        SubscriptionPlan plan = SubscriptionPlan.valueOf(subscriptionPlan == null ? "FREE" : subscriptionPlan.toUpperCase());
        long activeStrategies = strategyRepository.countActiveByUserId(userId);
        long limit = activeStrategyLimit(plan);

        if (limit != Long.MAX_VALUE && activeStrategies >= limit) {
            throw new InsufficientSubscriptionException(nextTier(plan));
        }
    }

    private long activeStrategyLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> 1L;
            case BASIC -> 5L;
            case PREMIUM -> Long.MAX_VALUE;
        };
    }

    private SubscriptionPlan nextTier(SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> SubscriptionPlan.BASIC;
            case BASIC, PREMIUM -> SubscriptionPlan.PREMIUM;
        };
    }
}
