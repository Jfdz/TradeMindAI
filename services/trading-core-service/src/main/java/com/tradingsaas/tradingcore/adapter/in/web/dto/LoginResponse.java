package com.tradingsaas.tradingcore.adapter.in.web.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
