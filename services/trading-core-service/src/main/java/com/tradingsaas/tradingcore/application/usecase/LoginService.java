package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.InvalidCredentialsException;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.LoginUseCase;
import com.tradingsaas.tradingcore.domain.port.out.JwtTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.RefreshTokenPort;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class LoginService implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenPort jwtTokenPort;
    private final RefreshTokenPort refreshTokenPort;

    LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                 JwtTokenPort jwtTokenPort, RefreshTokenPort refreshTokenPort) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenPort = jwtTokenPort;
        this.refreshTokenPort = refreshTokenPort;
    }

    @Override
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String plan = user.getSubscription() != null
                ? user.getSubscription().getPlan().name()
                : "FREE";

        String accessToken = jwtTokenPort.generateAccessToken(user.getId(), user.getEmail(), plan);
        String refreshToken = refreshTokenPort.generateAndStore(user.getId());

        return new AuthTokens(accessToken, refreshToken);
    }
}
