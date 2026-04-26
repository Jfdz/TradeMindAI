package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.exception.UserNotFoundException;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.model.UserNotificationPreferences;
import com.tradingsaas.tradingcore.domain.port.out.UserNotificationPreferencesRepository;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final UserNotificationPreferences DEFAULT_PREFERENCES = new UserNotificationPreferences(
            null,
            true,
            true,
            true,
            false,
            true,
            null,
            null
    );

    private final UserRepository userRepository;
    private final UserNotificationPreferencesRepository notificationPreferencesRepository;

    UserAccountService(UserRepository userRepository,
                       UserNotificationPreferencesRepository notificationPreferencesRepository) {
        this.userRepository = userRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
    }

    @Transactional(readOnly = true)
    public User getProfile(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User updateProfile(UUID userId, String firstName, String lastName, String timezone) {
        User current = getProfile(userId);
        User updated = new User(
                current.getId(),
                current.getEmail(),
                current.getPasswordHash(),
                firstName,
                lastName,
                timezone,
                current.getSubscription(),
                current.getCreatedAt(),
                current.isActive()
        );
        return userRepository.save(updated);
    }

    @Transactional
    public UserNotificationPreferences getNotificationPreferences(UUID userId) {
        return notificationPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> notificationPreferencesRepository.save(
                        new UserNotificationPreferences(
                                userId,
                                DEFAULT_PREFERENCES.signalDigest(),
                                DEFAULT_PREFERENCES.liveAlerts(),
                                DEFAULT_PREFERENCES.riskWarnings(),
                                DEFAULT_PREFERENCES.strategyChanges(),
                                DEFAULT_PREFERENCES.weeklyRecap(),
                                null,
                                null
                        )));
    }

    @Transactional
    public UserNotificationPreferences updateNotificationPreferences(UUID userId,
                                                                     boolean signalDigest,
                                                                     boolean liveAlerts,
                                                                     boolean riskWarnings,
                                                                     boolean strategyChanges,
                                                                     boolean weeklyRecap) {
        return notificationPreferencesRepository.save(new UserNotificationPreferences(
                userId,
                signalDigest,
                liveAlerts,
                riskWarnings,
                strategyChanges,
                weeklyRecap,
                getNotificationPreferences(userId).createdAt(),
                null
        ));
    }
}
