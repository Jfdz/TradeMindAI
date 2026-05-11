package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserLoginAuditJpaEntity;
import com.tradingsaas.tradingcore.domain.port.out.UserLoginAuditPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserLoginAuditJpaAdapter implements UserLoginAuditPort {

    private final UserLoginAuditJpaRepository repository;

    UserLoginAuditJpaAdapter(UserLoginAuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UUID userId, String ipAddress, String userAgent, String refreshTokenHash) {
        repository.save(new UserLoginAuditJpaEntity(userId, Instant.now(), ipAddress, userAgent, refreshTokenHash));
    }

    @Override
    public List<LoginAuditEntry> listRecent(UUID userId, int limit) {
        return repository.findRecentByUserId(userId, limit).stream()
                .map(e -> new LoginAuditEntry(e.getId(), e.getUserId(), e.getLoggedInAt(),
                        e.getIpAddress(), e.getUserAgent()))
                .toList();
    }
}
