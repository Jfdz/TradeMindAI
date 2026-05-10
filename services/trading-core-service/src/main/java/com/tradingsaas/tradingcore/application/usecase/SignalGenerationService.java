package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.config.RabbitMQConfig;
import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GenerateSignalUseCase;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class SignalGenerationService implements GenerateSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);
    private static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_TAKE_PROFIT_PCT = new BigDecimal("4.00");

    private final TradingSignalRepository tradingSignalRepository;
    private final HistoricalMarketDataPort marketDataPort;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort,
                            RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper) {
        this.tradingSignalRepository = tradingSignalRepository;
        this.marketDataPort = marketDataPort;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort) {
        this(tradingSignalRepository, marketDataPort, null, null);
    }

    @Override
    public TradingSignal generate(UUID symbolId, AiPrediction prediction) {
        BigDecimal entryPrice = fetchLatestPrice(prediction.getTicker());
        TradingSignal signal = new TradingSignal(
                UUID.randomUUID(),
                symbolId,
                prediction.getTicker(),
                prediction.getSignalType(),
                prediction.getConfidence(),
                Timeframe.DAILY,
                Instant.now(),
                riskStopLossPct(prediction.getSignalType()),
                riskTakeProfitPct(prediction.getSignalType()),
                prediction.getPredictedChangePct(),
                entryPrice);
        TradingSignal saved = tradingSignalRepository.save(signal);
        publishReasoningRequested(saved);
        return saved;
    }

    private void publishReasoningRequested(TradingSignal signal) {
        if (rabbitTemplate == null || objectMapper == null) return;
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("signalId", signal.getId().toString());
            event.put("ticker", signal.getTicker());
            event.put("signalType", signal.getType() != null ? signal.getType().name() : null);
            event.put("confidence", signal.getConfidence() != null ? signal.getConfidence().getValue() : null);
            event.put("predictedChangePct", signal.getPredictedChangePct());
            event.put("entryPrice", signal.getEntryPrice());
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REASONING_QUEUE, payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to publish reasoning event for signal {}: {}", signal.getId(), e.getMessage());
        }
    }

    private BigDecimal fetchLatestPrice(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        try {
            Map<String, BigDecimal> prices = marketDataPort.loadLatestPrices(List.of(ticker));
            return prices.get(ticker);
        } catch (Exception e) {
            log.warn("Could not fetch entry price for ticker={}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private BigDecimal riskStopLossPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_STOP_LOSS_PCT;
    }

    private BigDecimal riskTakeProfitPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_TAKE_PROFIT_PCT;
    }
}
