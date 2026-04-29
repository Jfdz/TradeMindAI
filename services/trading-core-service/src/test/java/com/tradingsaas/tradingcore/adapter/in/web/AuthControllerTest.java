package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.LoginResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.RefreshResponse;
import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.port.in.LoginUseCase;
import com.tradingsaas.tradingcore.domain.port.in.LogoutUseCase;
import com.tradingsaas.tradingcore.domain.port.in.RefreshTokenUseCase;
import com.tradingsaas.tradingcore.domain.port.in.RegisterUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    @Test
    void loginSetsSecureRefreshCookieWithSameSiteLax() {
        RegisterUserUseCase registerUserUseCase = mock(RegisterUserUseCase.class);
        LoginUseCase loginUseCase = mock(LoginUseCase.class);
        RefreshTokenUseCase refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
        JwtProperties jwtProperties = jwtProperties();
        AuthController controller = new AuthController(
                registerUserUseCase, loginUseCase, refreshTokenUseCase, logoutUseCase, jwtProperties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(loginUseCase.login("user@example.com", "secret"))
                .thenReturn(new LoginUseCase.AuthTokens("access-token", "refresh-token"));

        LoginResponse loginResponse = controller.login(new LoginRequest("user@example.com", "secret"), response);

        assertEquals("access-token", loginResponse.accessToken());
        String setCookie = response.getHeader("Set-Cookie");
        assertTrue(setCookie.contains("refresh_token=refresh-token"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=7200"));
    }

    @Test
    void refreshRotatesRefreshCookie() {
        AuthController controller = new AuthController(
                mock(RegisterUserUseCase.class),
                mock(LoginUseCase.class),
                mock(RefreshTokenUseCase.class),
                mock(LogoutUseCase.class),
                jwtProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshTokenUseCase refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        controller = new AuthController(
                mock(RegisterUserUseCase.class),
                mock(LoginUseCase.class),
                refreshTokenUseCase,
                mock(LogoutUseCase.class),
                jwtProperties());

        when(refreshTokenUseCase.refresh("old-refresh"))
                .thenReturn(new LoginUseCase.AuthTokens("new-access", "new-refresh"));

        RefreshResponse refreshResponse = controller.refresh("old-refresh", response);

        assertEquals("new-access", refreshResponse.accessToken());
        assertTrue(response.getHeader("Set-Cookie").contains("refresh_token=new-refresh"));
    }

    @Test
    void logoutClearsCookieAndPassesBearerTokenToUseCase() {
        LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
        AuthController controller = new AuthController(
                mock(RegisterUserUseCase.class),
                mock(LoginUseCase.class),
                mock(RefreshTokenUseCase.class),
                logoutUseCase,
                jwtProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer access-token");

        controller.logout("refresh-token", request, response);

        verify(logoutUseCase).logout("refresh-token", "access-token");
        assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=0"));
        assertTrue(response.getHeader("Set-Cookie").contains("SameSite=Lax"));
    }

    @Test
    void logoutWithoutRefreshTokenOnlyClearsCookie() {
        LogoutUseCase logoutUseCase = mock(LogoutUseCase.class);
        AuthController controller = new AuthController(
                mock(RegisterUserUseCase.class),
                mock(LoginUseCase.class),
                mock(RefreshTokenUseCase.class),
                logoutUseCase,
                jwtProperties());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(null, request, response);

        verify(logoutUseCase, never()).logout(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertTrue(response.getHeader("Set-Cookie").contains("refresh_token="));
    }

    private static JwtProperties jwtProperties() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenExpiry(900);
        jwtProperties.setRefreshTokenExpiry(7200);
        return jwtProperties;
    }
}
