package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserNotificationPreferencesJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserNotificationPreferencesJpaRepository extends JpaRepository<UserNotificationPreferencesJpaEntity, UUID> {

    Optional<UserNotificationPreferencesJpaEntity> findByUserId(UUID userId);
}
