package com.tradingsaas.tradingcore.domain.port.out;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenPort {

    String generateAndStore(UUID userId);

    Optional<UUID> getUserId(String token);

    void invalidate(String token);
}
