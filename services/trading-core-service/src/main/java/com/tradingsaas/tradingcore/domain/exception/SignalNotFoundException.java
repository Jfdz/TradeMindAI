package com.tradingsaas.tradingcore.domain.exception;

import java.util.UUID;

/** Raised when an operation targets a signal id that does not exist. */
public class SignalNotFoundException extends RuntimeException {

    public SignalNotFoundException(UUID signalId) {
        super("signal not found: " + signalId);
    }
}
