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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PredictionResultListener {

    private static final Logger log = LoggerFactory.getLogger(PredictionResultListener.class);
    public static final String EXCHANGE_NAME = "prediction.result.completed";
    public static final String QUEUE_NAME = "trading-core.prediction.result.completed";

    private final GenerateSignalUseCase generateSignalUseCase;
    private final ObjectMapper objectMapper;

    public PredictionResultListener(GenerateSignalUseCase generateSignalUseCase, ObjectMapper objectMapper) {
        this.generateSignalUseCase = generateSignalUseCase;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void onPredictionResult(String payload) {
        try {
            PredictionResultEvent event = objectMapper.readValue(payload, PredictionResultEvent.class);
            if (event.predictions() == null || event.predictions().isEmpty()) {
                log.warn("Prediction result event contained no predictions: tickers={}", event.tickers());
                return;
            }
            for (PredictionDto prediction : event.predictions()) {
                if (!prediction.isValid()) {
                    log.warn(
                            "Skipping invalid prediction result: ticker={} direction={} confidence={} predictedChangePct={}",
                            prediction.ticker(), prediction.direction(), prediction.confidence(), prediction.predictedChangePct());
                    continue;
                }
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

        boolean isValid() {
            return ticker != null
                    && !ticker.isBlank()
                    && !Double.isNaN(confidence)
                    && confidence >= 0.0
                    && confidence <= 1.0
                    && !Double.isNaN(predictedChangePct)
                    && !Double.isInfinite(predictedChangePct);
        }

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
