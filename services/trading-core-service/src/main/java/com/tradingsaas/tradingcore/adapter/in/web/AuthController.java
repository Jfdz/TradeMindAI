package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RefreshResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RegisterRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RegisterResponse;
import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.exception.TokenBlacklistedException;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.LoginUseCase;
import com.tradingsaas.tradingcore.domain.port.in.LogoutUseCase;
import com.tradingsaas.tradingcore.domain.port.in.RefreshTokenUseCase;
import com.tradingsaas.tradingcore.domain.port.in.RegisterUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final JwtProperties jwtProperties;

    AuthController(RegisterUserUseCase registerUserUseCase,
                   LoginUseCase loginUseCase,
                   RefreshTokenUseCase refreshTokenUseCase,
                   LogoutUseCase logoutUseCase,
                   JwtProperties jwtProperties) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUserUseCase.register(new RegisterUserUseCase.RegisterCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName()
        ));
        return toRegisterResponse(user);
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginUseCase.AuthTokens tokens = loginUseCase.login(request.email(), request.password());
        setRefreshTokenCookie(response, tokens.refreshToken());
        return new LoginResponse(tokens.accessToken(), "Bearer", jwtProperties.getAccessTokenExpiry());
    }

    @PostMapping("/refresh")
    RefreshResponse refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new TokenBlacklistedException();
        }
        LoginUseCase.AuthTokens tokens = refreshTokenUseCase.refresh(refreshToken);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return new RefreshResponse(tokens.accessToken(), "Bearer", jwtProperties.getAccessTokenExpiry());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (StringUtils.hasText(refreshToken)) {
            String accessToken = extractBearer(request);
            logoutUseCase.logout(refreshToken, accessToken);
        }
        clearRefreshTokenCookie(response);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(jwtProperties.getRefreshTokenExpiry())
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private RegisterResponse toRegisterResponse(User user) {
        String plan = user.getSubscription() != null
                ? user.getSubscription().getPlan().name()
                : "FREE";
        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                plan,
                user.getCreatedAt()
        );
    }
}
