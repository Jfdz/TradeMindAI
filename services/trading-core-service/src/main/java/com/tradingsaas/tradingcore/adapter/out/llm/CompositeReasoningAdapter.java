package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class CompositeReasoningAdapter implements ReasoningGenerator {

    private static final Logger log = LoggerFactory.getLogger(CompositeReasoningAdapter.class);

    private final GeminiReasoningAdapter gemini;
    private final GroqReasoningAdapter groq;

    public CompositeReasoningAdapter(GeminiReasoningAdapter gemini, GroqReasoningAdapter groq) {
        this.gemini = gemini;
        this.groq = groq;
    }

    @PostConstruct
    void logConfiguration() {
        if (!gemini.isConfigured() && !groq.isConfigured()) {
            log.warn("No LLM API keys configured. Reasoning will use deterministic fallback.");
        }
    }

    @Override
    public String generate(ReasoningContext context) {
        Optional<String> geminiResult = gemini.generate(context);
        if (geminiResult.isPresent()) {
            log.debug("Reasoning from Gemini for {}", context.ticker());
            return geminiResult.get();
        }
        Optional<String> groqResult = groq.generate(context);
        if (groqResult.isPresent()) {
            log.debug("Reasoning from Groq for {}", context.ticker());
            return groqResult.get();
        }
        throw new AllLlmAdaptersExhaustedException(
                "Both Gemini and Groq failed to produce valid reasoning for " + context.ticker());
    }
}
