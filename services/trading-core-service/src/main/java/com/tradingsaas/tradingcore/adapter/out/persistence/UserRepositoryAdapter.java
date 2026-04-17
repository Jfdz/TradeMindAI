package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SubscriptionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.mapper.UserEntityMapper;
import com.tradingsaas.tradingcore.domain.model.User;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final UserEntityMapper mapper;

    UserRepositoryAdapter(UserJpaRepository userJpaRepository,
                          SubscriptionJpaRepository subscriptionJpaRepository,
                          UserEntityMapper mapper) {
        this.userJpaRepository = userJpaRepository;
        this.subscriptionJpaRepository = subscriptionJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        Instant now = Instant.now();
        UserJpaEntity entity = new UserJpaEntity(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive(),
                user.getCreatedAt() != null ? user.getCreatedAt() : now,
                now
        );
        entity = userJpaRepository.save(entity);

        if (user.getSubscription() != null) {
            var sub = user.getSubscription();
            SubscriptionJpaEntity subEntity = new SubscriptionJpaEntity(
                    sub.getId(),
                    entity,
                    sub.getPlan(),
                    sub.getCreatedAt() != null ? sub.getCreatedAt() : now,
                    sub.getExpiresAt()
            );
            subscriptionJpaRepository.save(subEntity);
            entity.setSubscription(subEntity);
        }

        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }
}
