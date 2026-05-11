package com.tradingsaas.tradingcore.domain.port.in;

public interface LoginUseCase {

    AuthTokens login(String email, String rawPassword, String ipAddress, String userAgent);

    record AuthTokens(String accessToken, String refreshToken) {}
}
