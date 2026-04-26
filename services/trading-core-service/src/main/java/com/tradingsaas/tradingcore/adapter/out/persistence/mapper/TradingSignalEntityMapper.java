package com.tradingsaas.tradingcore.adapter.out.persistence.mapper;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import org.springframework.stereotype.Component;

@Component
public class TradingSignalEntityMapper {

    public TradingSignalJpaEntity toEntity(TradingSignal signal) {
        return new TradingSignalJpaEntity(
                signal.getId(),
                signal.getSymbolId(),
                signal.getTicker(),
                signal.getType(),
                signal.getConfidence().getValue(),
                signal.getTimeframe(),
                signal.getGeneratedAt(),
                signal.getStopLossPct(),
                signal.getTakeProfitPct(),
                signal.getPredictedChangePct());
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
                entity.getPredictedChangePct());
    }
}
