package com.tradingsaas.marketdata.enrichment.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisEnrichmentCacheAdapterTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisEnrichmentCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();

        adapter = new RedisEnrichmentCacheAdapter(template, new ObjectMapper());
    }

    @Test
    void putAndGetRoundtripForCompanyProfile() {
        CompanyProfile profile = new CompanyProfile("AAPL", "Apple Inc", null, "US", "USD", "NASDAQ", null, null, null, null, "Technology");

        adapter.put("enrichment:profile:AAPL", profile, Duration.ofMinutes(5));
        Optional<CompanyProfile> result = adapter.get("enrichment:profile:AAPL", CompanyProfile.class);

        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().ticker());
        assertEquals("Apple Inc", result.get().name());
        assertEquals("Technology", result.get().industry());
    }

    @Test
    void getMissingKeyReturnsEmpty() {
        Optional<CompanyProfile> result = adapter.get("enrichment:profile:UNKNOWN", CompanyProfile.class);

        assertFalse(result.isPresent());
    }

    @Test
    void evictRemovesKey() {
        CompanyProfile profile = new CompanyProfile("MSFT", "Microsoft", null, "US", "USD", "NASDAQ", null, null, null, null, null);
        adapter.put("enrichment:profile:MSFT", profile, Duration.ofMinutes(5));

        adapter.evict("enrichment:profile:MSFT");

        assertFalse(adapter.get("enrichment:profile:MSFT", CompanyProfile.class).isPresent());
    }

    @Test
    void putWithShortTtlExpiresKey() throws InterruptedException {
        CompanyProfile profile = new CompanyProfile("NVDA", "NVIDIA", null, null, null, null, null, null, null, null, null);
        adapter.put("enrichment:profile:NVDA", profile, Duration.ofMillis(200));

        Thread.sleep(400);

        assertFalse(adapter.get("enrichment:profile:NVDA", CompanyProfile.class).isPresent());
    }
}
