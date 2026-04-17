package com.tradingsaas.tradingcore.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class AiEngineClientConfig {

    @Bean
    WebClient aiEngineWebClient(WebClient.Builder builder, AiEngineProperties properties) {
        return builder.baseUrl(properties.getServiceUrl()).build();
    }

    @Bean
    CircuitBreakerRegistry aiPredictionCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
}
