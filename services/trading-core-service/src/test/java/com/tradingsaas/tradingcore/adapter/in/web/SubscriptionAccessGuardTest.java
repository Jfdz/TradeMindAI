package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SubscriptionAccessGuardTest {

    private final SubscriptionAccessGuard guard = new SubscriptionAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void freePlanCannotAccessMoreThanOneYearOfHistory() {
        authenticate("FREE");

        assertThrows(InsufficientSubscriptionException.class, () ->
                guard.requireHistoricalPriceAccess(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 4, 29)));
    }

    @Test
    void freePlanCanAccessOneYearOfHistory() {
        authenticate("FREE");

        assertDoesNotThrow(() ->
                guard.requireHistoricalPriceAccess(LocalDate.of(2025, 4, 29), LocalDate.of(2026, 4, 29)));
    }

    @Test
    void basicPlanCanAccessMoreThanOneYearOfHistory() {
        authenticate("BASIC");

        assertDoesNotThrow(() ->
                guard.requireHistoricalPriceAccess(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 4, 29)));
    }

    private void authenticate(String plan) {
        TokenClaims claims = new TokenClaims(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "guard@example.com",
                plan);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null));
    }
}
