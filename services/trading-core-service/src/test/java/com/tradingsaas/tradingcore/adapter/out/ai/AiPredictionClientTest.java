package com.tradingsaas.tradingcore.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingsaas.tradingcore.config.AiEngineProperties;
import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AiPredictionClientTest {

    @Test
    void mapsAiEnginePredictionToDomainModel() {
        AiPredictionClient client = createClient(request -> {
            assertEquals(HttpMethod.POST, request.method());
            assertEquals("/api/v1/predict", request.url().getPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "ticker": "aapl",
                              "direction": "UP",
                              "confidence": 0.91,
                              "predicted_change_pct": 1.4,
                              "raw_logits": [0.1, 0.8, 0.1]
                            }
                            """)
                    .build());
        });

        AiPrediction prediction = client.predict("aapl");

        assertEquals("AAPL", prediction.getTicker());
        assertEquals(SignalType.BUY, prediction.getSignalType());
        assertEquals(new BigDecimal("0.91"), prediction.getConfidence().getValue());
        assertEquals(new BigDecimal("1.4"), prediction.getPredictedChangePct());
        assertEquals(List.of(new BigDecimal("0.1"), new BigDecimal("0.8"), new BigDecimal("0.1")), prediction.getRawLogits());
    }

    @Test
    void fallbackReturnsHoldPredictionWhenEngineFails() {
        AiPredictionClient client = createClient(request -> Mono.error(new RuntimeException("boom")));

        AiPrediction prediction = client.predict("msft");

        assertEquals("MSFT", prediction.getTicker());
        assertEquals(SignalType.HOLD, prediction.getSignalType());
        assertEquals(BigDecimal.ZERO, prediction.getConfidence().getValue());
        assertEquals(BigDecimal.ZERO, prediction.getPredictedChangePct());
        assertEquals(List.of(), prediction.getRawLogits());
    }

    private AiPredictionClient createClient(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        AiEngineProperties properties = new AiEngineProperties();
        properties.setServiceUrl("http://localhost:8000");
        properties.setTimeout(Duration.ofSeconds(5));
        return new AiPredictionClient(webClient, properties, CircuitBreakerRegistry.ofDefaults());
    }
}
