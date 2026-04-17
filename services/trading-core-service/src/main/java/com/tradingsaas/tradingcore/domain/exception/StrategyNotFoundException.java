package com.tradingsaas.tradingcore.domain.exception;

public class StrategyNotFoundException extends RuntimeException {

    public StrategyNotFoundException(String message) {
        super(message);
    }
}
