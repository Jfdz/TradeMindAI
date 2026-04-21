package com.tradingsaas.tradingcore.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class RateLimitConfig {

    @Bean
    LettuceBasedProxyManager<String> rateLimitProxyManager(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        RedisURI.Builder uriBuilder = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isBlank()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        RedisURI uri = uriBuilder.build();
        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, byte[]> conn = client.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.builderFor(conn)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofDays(2)))
                .build();
    }
}
