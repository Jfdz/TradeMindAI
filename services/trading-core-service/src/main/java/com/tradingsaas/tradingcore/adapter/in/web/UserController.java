package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.UserAccountService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.model.UserNotificationPreferences;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserAccountService userAccountService;

    UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    UserProfileResponse getProfile(Authentication authentication) {
        TokenClaims claims = claims(authentication);
        return UserProfileResponse.from(userAccountService.getProfile(claims.userId()));
    }

    @PatchMapping("/me")
    UserProfileResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateUserProfileRequest request) {
        TokenClaims claims = claims(authentication);
        return UserProfileResponse.from(userAccountService.updateProfile(
                claims.userId(),
                request.firstName(),
                request.lastName(),
                request.timezone()
        ));
    }

    @GetMapping("/me/notifications")
    NotificationPreferencesResponse getNotificationPreferences(Authentication authentication) {
        TokenClaims claims = claims(authentication);
        return NotificationPreferencesResponse.from(userAccountService.getNotificationPreferences(claims.userId()));
    }

    @PutMapping("/me/notifications")
    NotificationPreferencesResponse updateNotificationPreferences(
            Authentication authentication,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        TokenClaims claims = claims(authentication);
        return NotificationPreferencesResponse.from(userAccountService.updateNotificationPreferences(
                claims.userId(),
                request.signalDigest(),
                request.liveAlerts(),
                request.riskWarnings(),
                request.strategyChanges(),
                request.weeklyRecap()
        ));
    }

    private TokenClaims claims(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof TokenClaims claims) {
            return claims;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    record UpdateUserProfileRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String timezone) {}

    record UpdateNotificationPreferencesRequest(
            boolean signalDigest,
            boolean liveAlerts,
            boolean riskWarnings,
            boolean strategyChanges,
            boolean weeklyRecap) {}

    record UserProfileResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String timezone,
            String plan,
            Instant createdAt,
            boolean active) {

        static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getTimezone(),
                    user.getSubscription() == null ? "FREE" : user.getSubscription().getPlan().name(),
                    user.getCreatedAt(),
                    user.isActive()
            );
        }
    }

    record NotificationPreferencesResponse(
            UUID userId,
            boolean signalDigest,
            boolean liveAlerts,
            boolean riskWarnings,
            boolean strategyChanges,
            boolean weeklyRecap,
            Instant createdAt,
            Instant updatedAt) {

        static NotificationPreferencesResponse from(UserNotificationPreferences preferences) {
            return new NotificationPreferencesResponse(
                    preferences.userId(),
                    preferences.signalDigest(),
                    preferences.liveAlerts(),
                    preferences.riskWarnings(),
                    preferences.strategyChanges(),
                    preferences.weeklyRecap(),
                    preferences.createdAt(),
                    preferences.updatedAt()
            );
        }
    }
}
