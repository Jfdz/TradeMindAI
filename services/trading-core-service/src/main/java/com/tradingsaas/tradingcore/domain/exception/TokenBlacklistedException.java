package com.tradingsaas.tradingcore.domain.exception;

public class TokenBlacklistedException extends RuntimeException {

    public TokenBlacklistedException() {
        super("Token has been revoked");
    }
}
