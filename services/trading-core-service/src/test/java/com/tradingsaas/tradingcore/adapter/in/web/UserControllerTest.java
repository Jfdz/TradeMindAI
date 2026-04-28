package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.application.usecase.UserAccountService;
import com.tradingsaas.tradingcore.domain.model.Subscription;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.model.UserNotificationPreferences;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class UserControllerTest {

    @Test
    void getProfileMapsCurrentUser() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserController controller = new UserController(userAccountService);
        when(userAccountService.getProfile(userId)).thenReturn(user(userId));

        UserController.UserProfileResponse response = controller.getProfile(auth(userId, "PREMIUM"));

        assertEquals(userId, response.id());
        assertEquals("user@example.com", response.email());
        assertEquals("PREMIUM", response.plan());
        assertEquals("Europe/Madrid", response.timezone());
    }

    @Test
    void updateProfileDelegatesUsingAuthenticatedUserId() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserController controller = new UserController(userAccountService);
        User updated = user(userId);
        when(userAccountService.updateProfile(userId, "Jane", "Quant", "UTC")).thenReturn(updated);

        UserController.UserProfileResponse response = controller.updateProfile(
                auth(userId, "BASIC"),
                new UserController.UpdateUserProfileRequest("Jane", "Quant", "UTC"));

        verify(userAccountService).updateProfile(userId, "Jane", "Quant", "UTC");
        assertEquals("user@example.com", response.email());
    }

    @Test
    void notificationsEndpointsRoundTripPreferences() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserController controller = new UserController(userAccountService);
        UserNotificationPreferences preferences = new UserNotificationPreferences(
                userId,
                true,
                false,
                true,
                false,
                true,
                Instant.parse("2026-04-20T10:00:00Z"),
                Instant.parse("2026-04-21T10:00:00Z"));

        when(userAccountService.getNotificationPreferences(userId)).thenReturn(preferences);
        when(userAccountService.updateNotificationPreferences(userId, false, true, false, true, false))
                .thenReturn(new UserNotificationPreferences(
                        userId,
                        false,
                        true,
                        false,
                        true,
                        false,
                        preferences.createdAt(),
                        Instant.parse("2026-04-22T10:00:00Z")));

        UserController.NotificationPreferencesResponse current =
                controller.getNotificationPreferences(auth(userId, "FREE"));
        UserController.NotificationPreferencesResponse updated =
                controller.updateNotificationPreferences(
                        auth(userId, "FREE"),
                        new UserController.UpdateNotificationPreferencesRequest(false, true, false, true, false));

        assertEquals(true, current.signalDigest());
        assertEquals(false, updated.signalDigest());
        assertEquals(true, updated.liveAlerts());
        verify(userAccountService).updateNotificationPreferences(userId, false, true, false, true, false);
    }

    @Test
    void rejectsMissingTokenClaims() {
        UserController controller = new UserController(mock(UserAccountService.class));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("not-claims");

        assertThrows(ResponseStatusException.class, () -> controller.getProfile(authentication));
    }

    private static Authentication auth(UUID userId, String plan) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new TokenClaims(userId, "user@example.com", plan));
        return authentication;
    }

    private static User user(UUID userId) {
        return new User(
                userId,
                "user@example.com",
                "$2a$10$hash",
                "Jane",
                "Quant",
                "Europe/Madrid",
                new Subscription(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        userId,
                        SubscriptionPlan.PREMIUM,
                        Instant.parse("2026-04-01T00:00:00Z"),
                        null),
                Instant.parse("2026-04-01T00:00:00Z"),
                true);
    }
}
