package com.tradingsaas.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * A calculated technical indicator value for a symbol and date.
 */
public record TechnicalIndicator(
        Symbol symbol,
        LocalDate date,
        TechnicalIndicatorType type,
        BigDecimal value,
        Map<String, String> metadata) {

    public TechnicalIndicator {
        symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        date = Objects.requireNonNull(date, "date must not be null");
        type = Objects.requireNonNull(type, "type must not be null");
        value = Objects.requireNonNull(value, "value must not be null");
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    public TechnicalIndicator(Symbol symbol, LocalDate date, TechnicalIndicatorType type, BigDecimal value) {
        this(symbol, date, type, value, Map.of());
    }
}
