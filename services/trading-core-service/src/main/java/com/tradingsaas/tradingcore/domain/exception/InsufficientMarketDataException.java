package com.tradingsaas.tradingcore.domain.exception;

public class InsufficientMarketDataException extends RuntimeException {
    public InsufficientMarketDataException(String message) {
        super(message);
    }
}
