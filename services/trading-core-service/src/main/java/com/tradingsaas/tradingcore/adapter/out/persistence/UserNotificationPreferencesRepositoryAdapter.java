package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserNotificationPreferencesJpaEntity;
import com.tradingsaas.tradingcore.domain.model.UserNotificationPreferences;
import com.tradingsaas.tradingcore.domain.port.out.UserNotificationPreferencesRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class UserNotificationPreferencesRepositoryAdapter implements UserNotificationPreferencesRepository {

    private final UserNotificationPreferencesJpaRepository repository;
    private final UserJpaRepository userJpaRepository;

    UserNotificationPreferencesRepositoryAdapter(UserNotificationPreferencesJpaRepository repository,
                                                 UserJpaRepository userJpaRepository) {
        this.repository = repository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserNotificationPreferences> findByUserId(UUID userId) {
        return repository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public UserNotificationPreferences save(UserNotificationPreferences preferences) {
        Instant now = Instant.now();
        UserJpaEntity user = userJpaRepository.findById(preferences.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + preferences.userId()));

        UserNotificationPreferencesJpaEntity entity = repository.findById(preferences.userId())
                .orElseGet(() -> new UserNotificationPreferencesJpaEntity(
                        preferences.userId(),
                        user,
                        preferences.signalDigest(),
                        preferences.liveAlerts(),
                        preferences.riskWarnings(),
                        preferences.strategyChanges(),
                        preferences.weeklyRecap(),
                        preferences.createdAt() != null ? preferences.createdAt() : now,
                        now));

        entity = new UserNotificationPreferencesJpaEntity(
                preferences.userId(),
                user,
                preferences.signalDigest(),
                preferences.liveAlerts(),
                preferences.riskWarnings(),
                preferences.strategyChanges(),
                preferences.weeklyRecap(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : now,
                now
        );

        return toDomain(repository.save(entity));
    }

    private UserNotificationPreferences toDomain(UserNotificationPreferencesJpaEntity entity) {
        return new UserNotificationPreferences(
                entity.getUserId(),
                entity.isSignalDigest(),
                entity.isLiveAlerts(),
                entity.isRiskWarnings(),
                entity.isStrategyChanges(),
                entity.isWeeklyRecap(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
