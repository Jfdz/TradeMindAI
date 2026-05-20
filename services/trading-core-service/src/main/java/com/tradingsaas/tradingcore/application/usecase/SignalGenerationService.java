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
import java.math.RoundingMode;
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
    // Minimum conviction threshold: predictions below this yield no actionable target/stop
    private static final BigDecimal MIN_TP_PCT = new BigDecimal("0.10");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    // Must match trading_signals.entry_price NUMERIC(18,6) (V15).
    private static final int ENTRY_PRICE_SCALE = 6;

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
            return backfillDerivedPricesIfMissing(existing.get());
        }
        // Derive TP/SL from prediction magnitude; HOLD always gets nulls
        BigDecimal tpPct;
        BigDecimal slPct;
        if (prediction.getSignalType() == SignalType.HOLD) {
            tpPct = null;
            slPct = null;
        } else {
            BigDecimal predicted = prediction.getPredictedChangePct();
            tpPct = (predicted != null) ? predicted.abs() : null;
            if (tpPct != null && tpPct.compareTo(MIN_TP_PCT) < 0) {
                tpPct = null;
            }
            slPct = (tpPct != null) ? tpPct.divide(TWO, 10, RoundingMode.HALF_EVEN) : null;
        }
        BigDecimal targetPrice = null;
        BigDecimal stopLoss = null;
        BigDecimal expectedMovePct = null;
        if (entryPrice != null && prediction.getSignalType() != SignalType.HOLD && tpPct != null) {
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
                return null;
            }
            // Match trading_signals.entry_price NUMERIC(18,6) so in-memory
            // equality (preflight findRecentEquivalent / DB-collision recovery)
            // matches the value the unique index sees after Postgres truncates
            // on INSERT. Upstream BigDecimals derived from float carry full
            // mantissa precision; without this they never compare equal to the
            // stored row and recovery throws "no equivalent row found".
            return price.setScale(ENTRY_PRICE_SCALE, RoundingMode.HALF_EVEN);
        } catch (Exception e) {
            log.warn("Could not fetch entry price for ticker={}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Self-heals an existing duplicate row when V21 columns are still null
     * (pre-V21 inserts, or rows where market-data was unavailable at
     * generation time but is reachable now). Without this, deploying Tier S
     * over a same-day window of legacy signals leaves them blank for the
     * full DUPLICATE_WINDOW and the validator pool stays empty.
     */
    private TradingSignal backfillDerivedPricesIfMissing(TradingSignal existing) {
        if (existing.getType() == SignalType.HOLD) {
            return existing;
        }
        if (existing.getEntryPrice() == null) {
            return existing;
        }
        if (existing.getTargetPrice() != null
                && existing.getStopLoss() != null
                && existing.getExpectedMovePct() != null) {
            return existing;
        }
        // Use stored pcts only — we cannot retroactively recompute the ATR at generation time
        BigDecimal tpPct = existing.getTakeProfitPct();
        BigDecimal slPct = existing.getStopLossPct();
        if (tpPct == null || slPct == null) {
            return existing;
        }
        BigDecimal targetPrice = signalMath.calculateTargetPrice(
                existing.getType(), existing.getEntryPrice(), tpPct);
        BigDecimal stopLoss = signalMath.calculateStopLoss(
                existing.getType(), existing.getEntryPrice(), slPct);
        BigDecimal expectedMovePct = signalMath.calculateExpectedMovePct(
                existing.getType(), existing.getEntryPrice(), targetPrice);
        signalMath.validatePriceCoherence(
                existing.getType(), existing.getEntryPrice(), targetPrice, stopLoss);
        TradingSignal healed = existing.withDerivedPrices(targetPrice, stopLoss, expectedMovePct);
        log.info("signal-generation: backfilling derived prices for existing signal id={} ticker={}",
                existing.getId(), existing.getTicker());
        return tradingSignalRepository.save(healed);
    }

}
