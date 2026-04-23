package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.StrategyJpaEntity;
import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import org.springframework.stereotype.Component;

@Component
public class StrategyEntityMapper {

    public StrategyJpaEntity toEntity(Strategy strategy) {
        RiskParameters riskParameters = strategy.getRiskParameters();
        return new StrategyJpaEntity(
                strategy.getId(),
                strategy.getUserId(),
                strategy.getName(),
                strategy.getDescription(),
                strategy.isActive(),
                riskParameters.getStopLossPct(),
                riskParameters.getTakeProfitPct(),
                riskParameters.getMaxPositionPct(),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt());
    }

    public Strategy toDomain(StrategyJpaEntity entity) {
        return new Strategy(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getDescription(),
                new RiskParameters(entity.getStopLossPct(), entity.getTakeProfitPct(), entity.getMaxPositionPct()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
