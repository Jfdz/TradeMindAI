package com.tradingsaas.tradingcore.adapter.out.cache;

import com.tradingsaas.tradingcore.config.JwtProperties;
import com.tradingsaas.tradingcore.domain.port.out.RefreshTokenPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class RedisRefreshTokenAdapter implements RefreshTokenPort {

    private static final String PREFIX = "trading-core:refresh:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    RedisRefreshTokenAdapter(StringRedisTemplate redis, JwtProperties props) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(props.getRefreshTokenExpiry());
    }

    @Override
    public String generateAndStore(UUID userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + token, userId.toString(), ttl);
        return token;
    }

    @Override
    public Optional<UUID> getUserId(String token) {
        String value = redis.opsForValue().get(PREFIX + token);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    @Override
    public void invalidate(String token) {
        redis.delete(PREFIX + token);
    }
}
