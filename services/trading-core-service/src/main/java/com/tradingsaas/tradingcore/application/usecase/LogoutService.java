package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.port.in.LogoutUseCase;
import com.tradingsaas.tradingcore.domain.port.out.RefreshTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.TokenBlacklistPort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
class LogoutService implements LogoutUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final JwtProperties jwtProperties;

    LogoutService(RefreshTokenPort refreshTokenPort,
                  TokenBlacklistPort tokenBlacklistPort,
                  JwtProperties jwtProperties) {
        this.refreshTokenPort = refreshTokenPort;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void logout(String refreshToken, String accessToken) {
        refreshTokenPort.invalidate(refreshToken);

        // Blacklist the access token for its remaining lifetime so it cannot be reused
        if (StringUtils.hasText(accessToken)) {
            tokenBlacklistPort.blacklist(accessToken, Duration.ofSeconds(jwtProperties.getAccessTokenExpiry()));
        }
    }
}
