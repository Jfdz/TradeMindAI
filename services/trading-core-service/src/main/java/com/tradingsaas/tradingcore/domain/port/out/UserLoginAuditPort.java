package com.tradingsaas.tradingcore.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserLoginAuditPort {

    void record(UUID userId, String ipAddress, String userAgent, String refreshTokenHash);

    List<LoginAuditEntry> listRecent(UUID userId, int limit);

    record LoginAuditEntry(
            UUID id,
            UUID userId,
            Instant loggedInAt,
            String ipAddress,
            String userAgent) {}
}
