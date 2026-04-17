package com.tradingsaas.tradingcore.domain.model.backtest;

import java.time.LocalDate;

public record BacktestRequest(
        String symbol,
        LocalDate from,
        LocalDate to,
        int quantity
) {
    public BacktestRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("Date range must not be null");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }
}
