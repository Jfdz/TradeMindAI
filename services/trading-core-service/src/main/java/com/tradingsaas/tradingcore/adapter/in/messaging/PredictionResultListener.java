package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.port.in.GenerateSignalUseCase;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PredictionResultListener {

    public static final String EXCHANGE_NAME = "prediction.result.completed";
    public static final String QUEUE_NAME = "trading-core.prediction.result.completed";

    private final GenerateSignalUseCase generateSignalUseCase;
    private final ObjectMapper objectMapper;

    public PredictionResultListener(GenerateSignalUseCase generateSignalUseCase, ObjectMapper objectMapper) {
        this.generateSignalUseCase = generateSignalUseCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = QUEUE_NAME, durable = "true"),
            exchange = @Exchange(value = EXCHANGE_NAME, durable = "true", type = ExchangeTypes.FANOUT)))
    public void onPredictionResult(String payload) {
        try {
            PredictionResultEvent event = objectMapper.readValue(payload, PredictionResultEvent.class);
            for (PredictionDto prediction : event.predictions()) {
                UUID symbolId = symbolIdForTicker(prediction.ticker());
                generateSignalUseCase.generate(symbolId, prediction.toDomain());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse prediction result event", ex);
        }
    }

    static UUID symbolIdForTicker(String ticker) {
        String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(normalizedTicker.getBytes(StandardCharsets.UTF_8));
    }

    private record PredictionResultEvent(List<String> tickers, List<PredictionDto> predictions) {}

    private record PredictionDto(
            String ticker,
            String direction,
            double confidence,
            @JsonProperty("predicted_change_pct") double predictedChangePct,
            @JsonProperty("raw_logits") List<Double> rawLogits) {

        AiPrediction toDomain() {
            return new AiPrediction(
                    ticker,
                    mapDirection(direction),
                    new Confidence(BigDecimal.valueOf(confidence)),
                    BigDecimal.valueOf(predictedChangePct),
                    rawLogits == null ? List.of() : rawLogits.stream().map(BigDecimal::valueOf).toList(),
                    Instant.now());
        }

        private SignalType mapDirection(String value) {
            if (value == null) {
                return SignalType.HOLD;
            }
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "UP" -> SignalType.BUY;
                case "DOWN" -> SignalType.SELL;
                default -> SignalType.HOLD;
            };
        }
    }
}
