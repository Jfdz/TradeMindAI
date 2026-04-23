package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestOrder(
        UUID orderId,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal requestedPrice,
        Instant submittedAt
) {
    public BacktestOrder {
        if (orderId == null) {
            throw new IllegalArgumentException("Order id must not be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        if (side == null) {
            throw new IllegalArgumentException("Side must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (requestedPrice == null || requestedPrice.signum() <= 0) {
            throw new IllegalArgumentException("Requested price must be positive");
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException("Submitted at must not be null");
        }
    }
}
