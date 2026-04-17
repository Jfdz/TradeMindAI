package com.tradingsaas.tradingcore.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradingsaas.tradingcore.config.AiEngineProperties;
import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.port.out.AiPredictionPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class AiPredictionClient implements AiPredictionPort {

    private final WebClient aiEngineWebClient;
    private final AiEngineProperties properties;
    private final CircuitBreaker circuitBreaker;

    AiPredictionClient(WebClient aiEngineWebClient, AiEngineProperties properties, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.aiEngineWebClient = aiEngineWebClient;
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiPredictionClient");
    }

    @Override
    public AiPrediction predict(String ticker) {
        Supplier<AiPrediction> supplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> callRemote(normalizeTicker(ticker)));
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return fallbackPrediction(ticker, ex);
        }
    }

    private AiPrediction callRemote(String normalizedTicker) {
        PredictionResponse response = aiEngineWebClient.post()
                .uri("/api/v1/predict")
                .bodyValue(new PredictRequest(normalizedTicker))
                .retrieve()
                .bodyToMono(PredictionResponse.class)
                .block(properties.getTimeout());

        if (response == null) {
            throw new IllegalStateException("AI engine returned no body");
        }

        return toDomain(response);
    }

    private AiPrediction fallbackPrediction(String ticker, Throwable throwable) {
        return AiPrediction.fallback(normalizeTicker(ticker));
    }

    private AiPrediction toDomain(PredictionResponse response) {
        SignalType signalType = mapDirection(response.direction());
        List<BigDecimal> logits = response.rawLogits() == null
                ? List.of()
                : response.rawLogits().stream().map(BigDecimal::valueOf).toList();
        return new AiPrediction(
                normalizeTicker(response.ticker()),
                signalType,
                new Confidence(BigDecimal.valueOf(response.confidence())),
                BigDecimal.valueOf(response.predictedChangePct()),
                logits,
                Instant.now());
    }

    private SignalType mapDirection(String direction) {
        if (direction == null) {
            return SignalType.HOLD;
        }
        return switch (direction.trim().toUpperCase(Locale.ROOT)) {
            case "UP" -> SignalType.BUY;
            case "DOWN" -> SignalType.SELL;
            default -> SignalType.HOLD;
        };
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private record PredictRequest(String ticker) {}

    private record PredictionResponse(
            String ticker,
            String direction,
            double confidence,
            @JsonProperty("predicted_change_pct") double predictedChangePct,
            @JsonProperty("raw_logits") List<Double> rawLogits) {}
}
