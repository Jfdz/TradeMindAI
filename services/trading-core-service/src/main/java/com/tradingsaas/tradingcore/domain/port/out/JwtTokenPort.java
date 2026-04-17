package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.TokenClaims;

import java.util.UUID;

public interface JwtTokenPort {

    String generateAccessToken(UUID userId, String email, String subscriptionPlan);

    TokenClaims validateAccessToken(String token);
}
