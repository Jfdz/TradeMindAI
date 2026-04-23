package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.InvalidCredentialsException;
import com.tradingsaas.tradingcore.domain.exception.TokenBlacklistedException;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.LoginUseCase.AuthTokens;
import com.tradingsaas.tradingcore.domain.port.in.RefreshTokenUseCase;
import com.tradingsaas.tradingcore.domain.port.out.JwtTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.RefreshTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class RefreshTokenService implements RefreshTokenUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final UserRepository userRepository;
    private final JwtTokenPort jwtTokenPort;

    RefreshTokenService(RefreshTokenPort refreshTokenPort,
                        UserRepository userRepository,
                        JwtTokenPort jwtTokenPort) {
        this.refreshTokenPort = refreshTokenPort;
        this.userRepository = userRepository;
        this.jwtTokenPort = jwtTokenPort;
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        UUID userId = refreshTokenPort.getUserId(refreshToken)
                .orElseThrow(TokenBlacklistedException::new);

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        String plan = user.getSubscription() != null
                ? user.getSubscription().getPlan().name()
                : "FREE";

        // rotate: invalidate old, issue new
        refreshTokenPort.invalidate(refreshToken);
        String newRefreshToken = refreshTokenPort.generateAndStore(userId);
        String newAccessToken = jwtTokenPort.generateAccessToken(userId, user.getEmail(), plan);

        return new AuthTokens(newAccessToken, newRefreshToken);
    }
}
