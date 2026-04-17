package com.tradingsaas.tradingcore.adapter.out.cache;

import com.tradingsaas.tradingcore.domain.port.out.TokenBlacklistPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String PREFIX = "trading-core:blacklist:";

    private final StringRedisTemplate redis;

    RedisTokenBlacklistAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String token, Duration ttl) {
        redis.opsForValue().set(PREFIX + token, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + token));
    }
}
