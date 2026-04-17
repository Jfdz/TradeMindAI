package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.Strategy;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ManageStrategiesUseCase {

    Page<Strategy> getStrategies(UUID userId, Pageable pageable);

    Strategy createStrategy(UUID userId, StrategyCommand command);

    Strategy updateStrategy(UUID userId, UUID strategyId, StrategyCommand command);

    void deleteStrategy(UUID userId, UUID strategyId);

    record StrategyCommand(
            String name,
            String description,
            BigDecimal stopLossPct,
            BigDecimal takeProfitPct,
            BigDecimal maxPositionPct,
            boolean active) {}
}
