package com.tradingsaas.marketdata.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

class MarketDataConfigTest {

    @Test
    void redisConfigCreatesRedisTemplateAndConnectionFactory() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("localhost");
        redisProperties.setPort(6379);
        redisProperties.setDatabase(0);
        redisProperties.setPassword("");

        RedisConfig config = new RedisConfig();

        LettuceConnectionFactory connectionFactory = config.redisConnectionFactory(redisProperties);
        RedisTemplate<String, Object> redisTemplate = config.redisTemplate(connectionFactory);

        assertInstanceOf(LettuceConnectionFactory.class, connectionFactory);
        assertNotNull(redisTemplate.getConnectionFactory());
    }

    @Test
    void rabbitConfigCreatesRabbitTemplateAndListenerFactory() {
        RabbitProperties rabbitProperties = new RabbitProperties();
        rabbitProperties.setHost("localhost");
        rabbitProperties.setPort(5672);
        rabbitProperties.setUsername("guest");
        rabbitProperties.setPassword("guest");
        rabbitProperties.setVirtualHost("/");

        RabbitMQConfig config = new RabbitMQConfig();

        CachingConnectionFactory connectionFactory = (CachingConnectionFactory) config.rabbitConnectionFactory(rabbitProperties);
        RabbitTemplate rabbitTemplate = config.rabbitTemplate(connectionFactory, config.rabbitMessageConverter());

        assertInstanceOf(CachingConnectionFactory.class, connectionFactory);
        assertNotNull(rabbitTemplate.getMessageConverter());
    }
}
