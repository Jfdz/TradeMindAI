package com.tradingsaas.tradingcore.adapter.in.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.out.llm.CompositeReasoningAdapter;
import com.tradingsaas.tradingcore.adapter.out.llm.DeterministicReasoningFallback;
import com.tradingsaas.tradingcore.adapter.out.news.CompositeNewsContextAdapter;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider.NewsHeadline;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.AllLlmAdaptersExhaustedException;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReasoningGenerationListenerTest {

    private static final UUID SIGNAL_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String PAYLOAD = """
            {
              "signalId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
              "ticker": "AAPL",
              "signalType": "BUY",
              "confidence": 0.85,
              "predictedChangePct": 1.5,
              "entryPrice": 182.50
            }
            """;

    @Test
    void updatesReasoningToReadyWhenLlmSucceeds() {
        CompositeReasoningAdapter reasoningGenerator = mock(CompositeReasoningAdapter.class);
        CompositeNewsContextAdapter newsProvider = mock(CompositeNewsContextAdapter.class);
        DeterministicReasoningFallback fallback = mock(DeterministicReasoningFallback.class);
        TradingSignalRepository repository = mock(TradingSignalRepository.class);

        when(newsProvider.fetchHeadlines("AAPL")).thenReturn(List.of(
                new NewsHeadline("Apple hits record high", "Reuters", Instant.now())));
        when(reasoningGenerator.generate(any(ReasoningContext.class)))
                .thenReturn("AAPL bullish breakout detected with 85% confidence.");

        ReasoningGenerationListener listener = new ReasoningGenerationListener(
                reasoningGenerator, newsProvider, fallback, repository, new ObjectMapper());

        listener.onReasoningRequested(PAYLOAD);

        verify(repository).updateReasoning(
                eq(SIGNAL_ID),
                eq("AAPL bullish breakout detected with 85% confidence."),
                eq(ReasoningStatus.READY),
                any(Instant.class));
        verify(fallback, never()).build(any());
    }

    @Test
    void updatesReasoningToFallbackWhenLlmExhausted() {
        CompositeReasoningAdapter reasoningGenerator = mock(CompositeReasoningAdapter.class);
        CompositeNewsContextAdapter newsProvider = mock(CompositeNewsContextAdapter.class);
        DeterministicReasoningFallback fallback = mock(DeterministicReasoningFallback.class);
        TradingSignalRepository repository = mock(TradingSignalRepository.class);

        when(newsProvider.fetchHeadlines("AAPL")).thenReturn(List.of());
        when(reasoningGenerator.generate(any(ReasoningContext.class)))
                .thenThrow(new AllLlmAdaptersExhaustedException("both API keys absent"));
        when(fallback.build(any(ReasoningContext.class)))
                .thenReturn("AAPL bullish breakout detected with 85% confidence.");

        ReasoningGenerationListener listener = new ReasoningGenerationListener(
                reasoningGenerator, newsProvider, fallback, repository, new ObjectMapper());

        listener.onReasoningRequested(PAYLOAD);

        verify(repository).updateReasoning(
                eq(SIGNAL_ID),
                eq("AAPL bullish breakout detected with 85% confidence."),
                eq(ReasoningStatus.FALLBACK),
                any(Instant.class));
    }

    @Test
    void deterministicFallbackProducesValidReasoningWhenNoNewsAvailable() {
        CompositeReasoningAdapter reasoningGenerator = mock(CompositeReasoningAdapter.class);
        CompositeNewsContextAdapter newsProvider = mock(CompositeNewsContextAdapter.class);
        DeterministicReasoningFallback fallback = new DeterministicReasoningFallback();
        TradingSignalRepository repository = mock(TradingSignalRepository.class);

        when(newsProvider.fetchHeadlines("AAPL")).thenReturn(List.of());
        when(reasoningGenerator.generate(any(ReasoningContext.class)))
                .thenThrow(new AllLlmAdaptersExhaustedException("both API keys absent"));

        ReasoningGenerationListener listener = new ReasoningGenerationListener(
                reasoningGenerator, newsProvider, fallback, repository, new ObjectMapper());

        listener.onReasoningRequested(PAYLOAD);

        verify(repository).updateReasoning(
                eq(SIGNAL_ID),
                argThat(text -> text != null && text.contains("AAPL") && text.length() <= 280),
                eq(ReasoningStatus.FALLBACK),
                any(Instant.class));
    }
}
