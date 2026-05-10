package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.adapter.out.llm.dto.GroqRequest;
import com.tradingsaas.tradingcore.adapter.out.llm.dto.GroqResponse;
import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.Optional;

@Component
public class GroqReasoningAdapter {

    private static final Logger log = LoggerFactory.getLogger(GroqReasoningAdapter.class);
    private static final String PROMPT =
            "Write one sentence (max 280 chars) explaining why %s shows a %s signal at %.0f%% confidence. "
            + "Recent news: %s. Be specific about price action or catalyst. No disclaimers.";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int maxOutputTokens;
    private final double temperature;
    private final LlmOutputValidator validator = new LlmOutputValidator();

    @Autowired
    public GroqReasoningAdapter(
            @Value("${trading-core.llm.groq.base-url:https://api.groq.com}") String baseUrl,
            @Value("${trading-core.llm.groq.api-key:}") String apiKey,
            @Value("${trading-core.llm.groq.model:llama-3.1-8b-instant}") String model,
            @Value("${trading-core.llm.groq.timeout-seconds:10}") int timeoutSeconds,
            @Value("${trading-core.llm.groq.max-output-tokens:100}") int maxOutputTokens,
            @Value("${trading-core.llm.groq.temperature:0.7}") double temperature) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    GroqReasoningAdapter(WebClient webClient, String apiKey, String model,
                         int timeoutSeconds, int maxOutputTokens, double temperature) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
    }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    public Optional<String> generate(ReasoningContext ctx) {
        if (!isConfigured()) { log.warn("Groq API key not configured"); return Optional.empty(); }
        String prompt = String.format(PROMPT,
                ctx.ticker(), ctx.signalType(),
                ctx.confidence().doubleValue() * 100,
                ctx.newsContext() != null ? ctx.newsContext() : "none");
        try {
            GroqResponse response = webClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(GroqRequest.of(model, prompt, maxOutputTokens, temperature))
                    .retrieve()
                    .bodyToMono(GroqResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            return validator.validate(response != null ? response.extractText() : null);
        } catch (Exception e) {
            log.warn("Groq call failed for {}: {}", ctx.ticker(), e.getMessage());
            return Optional.empty();
        }
    }
}
