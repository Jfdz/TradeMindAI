package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExecutionResult(
        UUID orderId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal requestedPrice,
        BigDecimal fillPrice,
        BigDecimal commission,
        BigDecimal cashImpact,
        BigDecimal slippageApplied,
        Instant executedAt
) {
}
