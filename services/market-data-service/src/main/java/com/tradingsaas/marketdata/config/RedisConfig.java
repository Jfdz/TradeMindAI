package com.tradingsaas.marketdata.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
        configuration.setDatabase(redisProperties.getDatabase());
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            configuration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        ObjectMapper redisObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        return template;
    }
}
