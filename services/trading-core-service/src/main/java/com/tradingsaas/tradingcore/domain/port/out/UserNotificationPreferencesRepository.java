package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.UserNotificationPreferences;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationPreferencesRepository {

    Optional<UserNotificationPreferences> findByUserId(UUID userId);

    UserNotificationPreferences save(UserNotificationPreferences preferences);
}
