package com.tradingsaas.tradingcore.adapter.in.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GenerateSignalUseCase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PredictionResultListenerTest {

    @Test
    void convertsPredictionEventIntoSignalGenerationCalls() throws Exception {
        RecordingUseCase useCase = new RecordingUseCase();
        PredictionResultListener listener = new PredictionResultListener(useCase, new ObjectMapper());

        listener.onPredictionResult("""
                {
                  "tickers": ["aapl"],
                  "predictions": [
                    {
                      "ticker": "aapl",
                      "direction": "UP",
                      "confidence": 0.85,
                      "predicted_change_pct": 1.4,
                      "raw_logits": [0.1, 0.8, 0.1]
                    }
                  ]
                }
                """);

        assertNotNull(useCase.lastSignal);
        UUID expectedSymbolId = PredictionResultListener.symbolIdForTicker("aapl");
        assertEquals(expectedSymbolId, useCase.lastSignal.getSymbolId());
        assertEquals("AAPL", useCase.lastPrediction.getTicker());
        assertEquals(new BigDecimal("0.85"), useCase.lastPrediction.getConfidence().getValue());
    }

    private static final class RecordingUseCase implements GenerateSignalUseCase {
        private TradingSignal lastSignal;
        private AiPrediction lastPrediction;

        @Override
        public TradingSignal generate(UUID symbolId, String ticker) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TradingSignal generate(UUID symbolId, AiPrediction prediction) {
            this.lastPrediction = prediction;
            this.lastSignal = new TradingSignal(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    symbolId,
                    prediction.getSignalType(),
                    prediction.getConfidence(),
                    com.tradingsaas.tradingcore.domain.model.Timeframe.DAILY,
                    java.time.Instant.parse("2026-04-17T10:00:00Z"),
                    new BigDecimal("2.00"),
                    new BigDecimal("4.00"));
            return lastSignal;
        }
    }
}
