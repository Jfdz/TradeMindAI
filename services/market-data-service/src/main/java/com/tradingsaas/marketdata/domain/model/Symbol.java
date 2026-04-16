package com.tradingsaas.marketdata.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Tracked market symbol metadata.
 */
public record Symbol(String ticker, String name, String exchange, String sector, boolean active) {

    public Symbol {
        ticker = normalizeRequired(ticker, "ticker").toUpperCase(Locale.ROOT);
        name = normalizeRequired(name, "name");
        exchange = normalizeRequired(exchange, "exchange").toUpperCase(Locale.ROOT);
        sector = sector == null ? "" : sector.trim();
    }

    public Symbol(String ticker, String name, String exchange) {
        this(ticker, name, exchange, "", true);
    }

    public boolean isActive() {
        return active;
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
