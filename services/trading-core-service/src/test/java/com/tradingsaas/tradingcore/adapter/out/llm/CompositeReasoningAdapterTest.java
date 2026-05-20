package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.AllLlmAdaptersExhaustedException;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompositeReasoningAdapterTest {

    @Mock GeminiReasoningAdapter gemini;
    @Mock GroqReasoningAdapter groq;
    @InjectMocks CompositeReasoningAdapter composite;

    private final ReasoningContext ctx =
            new ReasoningContext("AAPL", "BUY", BigDecimal.valueOf(0.85), "Strong sales data");

    @Test void returnsGeminiResultWhenGeminiSucceeds() {
        when(gemini.generate(ctx)).thenReturn(Optional.of("AAPL breaks out on iPhone demand."));
        assertThat(composite.generate(ctx)).isEqualTo("AAPL breaks out on iPhone demand.");
        verify(groq, never()).generate(any());
    }

    @Test void fallsBackToGroqWhenGeminiFails() {
        when(gemini.generate(ctx)).thenReturn(Optional.empty());
        when(groq.generate(ctx)).thenReturn(Optional.of("AAPL bounces off support on volume surge."));
        assertThat(composite.generate(ctx)).isEqualTo("AAPL bounces off support on volume surge.");
    }

    @Test void throwsSentinelWhenBothAdaptersFail() {
        when(gemini.generate(ctx)).thenReturn(Optional.empty());
        when(groq.generate(ctx)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> composite.generate(ctx))
                .isInstanceOf(AllLlmAdaptersExhaustedException.class)
                .hasMessageContaining("AAPL");
    }
}
