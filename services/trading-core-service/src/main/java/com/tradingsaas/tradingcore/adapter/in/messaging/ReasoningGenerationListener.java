package com.tradingsaas.tradingcore.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.llm.CompositeReasoningAdapter;
import com.tradingsaas.tradingcore.adapter.out.llm.DeterministicReasoningFallback;
import com.tradingsaas.tradingcore.adapter.out.news.CompositeNewsContextAdapter;
import com.tradingsaas.tradingcore.config.RabbitMQConfig;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider.NewsHeadline;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.AllLlmAdaptersExhaustedException;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Legacy in-process reasoning consumer (Gemini + deterministic fallback).
 *
 * <p>Gated behind {@code trading-core.reasoning.legacy-listener.enabled}
 * (default {@code false}). ai-engine's Track C grounded pipeline owns the
 * reasoning queue end-to-end; this listener is retained as a one-flag
 * emergency rollback path. Enabling both consumers simultaneously is a
 * split-brain bug — RabbitMQ would distribute messages across both,
 * producing mixed (validated vs Gemini-fallback) reasoning artifacts.
 */
@Component
@ConditionalOnProperty(
        prefix = "trading-core.reasoning",
        name = "legacy-listener.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ReasoningGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(ReasoningGenerationListener.class);

    private final CompositeReasoningAdapter reasoningGenerator;
    private final CompositeNewsContextAdapter newsProvider;
    private final DeterministicReasoningFallback deterministicFallback;
    private final TradingSignalRepository signalRepository;
    private final ObjectMapper objectMapper;

    public ReasoningGenerationListener(
            CompositeReasoningAdapter reasoningGenerator,
            CompositeNewsContextAdapter newsProvider,
            DeterministicReasoningFallback deterministicFallback,
            TradingSignalRepository signalRepository,
            ObjectMapper objectMapper) {
        this.reasoningGenerator = reasoningGenerator;
        this.newsProvider = newsProvider;
        this.deterministicFallback = deterministicFallback;
        this.signalRepository = signalRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.REASONING_QUEUE)
    public void onReasoningRequested(String payload) {
        try {
            Map<?, ?> event = objectMapper.readValue(payload, Map.class);
            UUID signalId = UUID.fromString((String) event.get("signalId"));
            String ticker = (String) event.get("ticker");
            String signalType = (String) event.get("signalType");
            BigDecimal confidence = parseBigDecimal(event.get("confidence"));

            log.debug("Processing reasoning for signal={} ticker={}", signalId, ticker);

            List<NewsHeadline> headlines = newsProvider.fetchHeadlines(ticker);
            String newsContext = headlines.stream()
                    .map(NewsHeadline::title)
                    .collect(Collectors.joining("; "));

            ReasoningContext ctx = new ReasoningContext(ticker, signalType, confidence, newsContext);
            try {
                String reasoning = reasoningGenerator.generate(ctx);
                signalRepository.updateReasoning(signalId, reasoning, ReasoningStatus.READY, Instant.now());
                log.info("Reasoning READY for signal={} ticker={}", signalId, ticker);
            } catch (AllLlmAdaptersExhaustedException e) {
                String fallback = deterministicFallback.build(ctx);
                signalRepository.updateReasoning(signalId, fallback, ReasoningStatus.FALLBACK, Instant.now());
                log.info("Reasoning FALLBACK for signal={} ticker={}: {}", signalId, ticker, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Reasoning generation failed for payload, using fallback: {}", e.getMessage());
            rethrowAsRuntime(e);
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(value.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private void rethrowAsRuntime(Exception e) {
        if (e instanceof RuntimeException re) throw re;
        throw new IllegalStateException("Reasoning listener failed", e);
    }
}
