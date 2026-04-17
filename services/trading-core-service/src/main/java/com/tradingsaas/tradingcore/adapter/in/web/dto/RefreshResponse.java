package com.tradingsaas.tradingcore.adapter.in.web.dto;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
