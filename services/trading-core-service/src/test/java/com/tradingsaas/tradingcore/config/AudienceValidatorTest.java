package com.tradingsaas.tradingcore.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("https://api.trademindai.com");

    @Test
    void acceptsJwtWithMatchingAudience() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of("https://api.trademindai.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void acceptsJwtWithMatchingAudienceAmongMany() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of("other-service", "https://api.trademindai.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsJwtWithNoMatchingAudience() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of("https://wrong.example.com"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getErrorCode().equals("invalid_token")));
    }

    @Test
    void rejectsJwtWithEmptyAudience() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of());

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
    }
}
