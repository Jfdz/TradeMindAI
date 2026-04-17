package com.tradingsaas.tradingcore.domain.port.out;

import java.time.Duration;

public interface TokenBlacklistPort {

    void blacklist(String token, Duration ttl);

    boolean isBlacklisted(String token);
}
