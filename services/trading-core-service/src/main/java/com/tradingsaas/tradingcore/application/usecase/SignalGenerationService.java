package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.application.service.SignalMathService;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class SignalGenerationService implements GenerateSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);
    private static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_TAKE_PROFIT_PCT = new BigDecimal("4.00");

    // A duplicate window of 24h aligns with the unique-per-day index on the DB side
    // (date_trunc('day', generated_at)) and tolerates clock skew across producers.
    private static final java.time.Duration DUPLICATE_WINDOW = java.time.Duration.of(24, ChronoUnit.HOURS);

    private final TradingSignalRepository tradingSignalRepository;
    private final HistoricalMarketDataPort marketDataPort;
    private final SignalMathService signalMath;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Counter dedupSkipCounter;
    private final Counter dedupDbCollisionCounter;

    @Autowired
    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort,
                            SignalMathService signalMath,
                            RabbitTemplate rabbitTemplate,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.tradingSignalRepository = tradingSignalRepository;
        this.marketDataPort = marketDataPort;
        this.signalMath = signalMath;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.dedupSkipCounter = Counter.builder("signal_generation_dedup_total")
                .tag("reason", "equivalent_within_window")
                .register(meterRegistry);
        this.dedupDbCollisionCounter = Counter.builder("signal_generation_dedup_total")
                .tag("reason", "db_unique_violation")
                .register(meterRegistry);
    }

    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort) {
        this(tradingSignalRepository, marketDataPort, new SignalMathService(), null, null,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort,
                            SignalMathService signalMath) {
        this(tradingSignalRepository, marketDataPort, signalMath, null, null,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Override
    public TradingSignal generate(UUID symbolId, AiPrediction prediction) {
        BigDecimal entryPrice = fetchLatestPrice(prediction.getTicker());
        Instant windowStart = Instant.now().minus(DUPLICATE_WINDOW);
        Optional<TradingSignal> existing = tradingSignalRepository.findRecentEquivalent(
                prediction.getTicker(), prediction.getSignalType(), Timeframe.DAILY,
                entryPrice, windowStart);
        if (existing.isPresent()) {
            dedupSkipCounter.increment();
            log.info("signal-generation: skipping duplicate ticker={} signalType={} entryPrice={} existingId={}",
                    prediction.getTicker(), prediction.getSignalType(), entryPrice, existing.get().getId());
            return existing.get();
        }
        BigDecimal slPct = riskStopLossPct(prediction.getSignalType());
        BigDecimal tpPct = riskTakeProfitPct(prediction.getSignalType());
        BigDecimal targetPrice = null;
        BigDecimal stopLoss = null;
        BigDecimal expectedMovePct = null;
        if (entryPrice != null && prediction.getSignalType() != SignalType.HOLD) {
            targetPrice = signalMath.calculateTargetPrice(prediction.getSignalType(), entryPrice, tpPct);
            stopLoss = signalMath.calculateStopLoss(prediction.getSignalType(), entryPrice, slPct);
            expectedMovePct = signalMath.calculateExpectedMovePct(prediction.getSignalType(), entryPrice, targetPrice);
            signalMath.validatePriceCoherence(prediction.getSignalType(), entryPrice, targetPrice, stopLoss);
        }
        TradingSignal signal = new TradingSignal(
                UUID.randomUUID(),
                symbolId,
                prediction.getTicker(),
                prediction.getSignalType(),
                prediction.getConfidence(),
                Timeframe.DAILY,
                Instant.now(),
                slPct,
                tpPct,
                prediction.getPredictedChangePct(),
                entryPrice,
                targetPrice,
                stopLoss,
                expectedMovePct);
        try {
            TradingSignal saved = tradingSignalRepository.save(signal);
            publishReasoningRequested(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert beat us between the findRecentEquivalent() check and the save();
            // the DB unique index caught it. Treat the existing row as success.
            dedupDbCollisionCounter.increment();
            log.info("signal-generation: DB unique index caught duplicate ticker={} signalType={} entryPrice={}; using existing row",
                    prediction.getTicker(), prediction.getSignalType(), entryPrice);
            return tradingSignalRepository.findRecentEquivalent(
                    prediction.getTicker(), prediction.getSignalType(), Timeframe.DAILY,
                    entryPrice, windowStart)
                    .orElseThrow(() -> new IllegalStateException(
                            "DB unique violation but no equivalent row found for " + prediction.getTicker(), e));
        }
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
            event.put("targetPrice", signal.getTargetPrice());
            event.put("stopLoss", signal.getStopLoss());
            event.put("expectedMovePct", signal.getExpectedMovePct());
            // C9 — ai-engine SignalInput requires generated_at. Older
            // messages without it default to message-receive time on
            // the consumer side, but new publishes carry the real value.
            event.put("generatedAt",
                    signal.getGeneratedAt() != null ? signal.getGeneratedAt().toString() : null);
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
            BigDecimal price = prices.get(ticker);
            if (price == null) {
                log.warn("signal-generation: no entry_price captured for ticker={} (market-data returned empty)", ticker);
            }
            return price;
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
