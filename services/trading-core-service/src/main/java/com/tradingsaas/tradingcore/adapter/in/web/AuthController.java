package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RegisterRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RegisterResponse;
import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.in.LoginUseCase;
import com.tradingsaas.tradingcore.domain.port.in.RegisterUserUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final JwtProperties jwtProperties;

    AuthController(RegisterUserUseCase registerUserUseCase,
                   LoginUseCase loginUseCase,
                   JwtProperties jwtProperties) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
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

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) jwtProperties.getRefreshTokenExpiry());
        response.addCookie(cookie);
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
