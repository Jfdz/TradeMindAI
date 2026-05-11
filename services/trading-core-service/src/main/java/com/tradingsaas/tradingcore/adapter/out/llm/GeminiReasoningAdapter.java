package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.adapter.out.llm.dto.GeminiRequest;
import com.tradingsaas.tradingcore.adapter.out.llm.dto.GeminiResponse;
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
public class GeminiReasoningAdapter {

    private static final Logger log = LoggerFactory.getLogger(GeminiReasoningAdapter.class);

    private final WebClient webClient;
    private final String apiKey;
    private final int timeoutSeconds;
    private final int maxOutputTokens;
    private final double temperature;
    private final LlmOutputValidator validator = new LlmOutputValidator();
    private final ReasoningPromptBuilder promptBuilder;

    @Autowired
    public GeminiReasoningAdapter(
            @Value("${trading-core.llm.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${trading-core.llm.gemini.api-key:}") String apiKey,
            @Value("${trading-core.llm.gemini.timeout-seconds:10}") int timeoutSeconds,
            @Value("${trading-core.llm.gemini.max-output-tokens:100}") int maxOutputTokens,
            @Value("${trading-core.llm.gemini.temperature:0.7}") double temperature,
            ReasoningPromptBuilder promptBuilder) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.promptBuilder = promptBuilder;
    }

    GeminiReasoningAdapter(WebClient webClient, String apiKey,
                           int timeoutSeconds, int maxOutputTokens, double temperature,
                           ReasoningPromptBuilder promptBuilder) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.promptBuilder = promptBuilder;
    }

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    public Optional<String> generate(ReasoningContext ctx) {
        if (!isConfigured()) { log.warn("Gemini API key not configured"); return Optional.empty(); }
        String prompt = promptBuilder.build(ctx);
        try {
            GeminiResponse response = webClient.post()
                    .uri(u -> u.path("/v1beta/models/gemini-1.5-flash-latest:generateContent")
                            .queryParam("key", apiKey).build())
                    .bodyValue(GeminiRequest.of(prompt, maxOutputTokens, temperature))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            return validator.validate(response != null ? response.extractText() : null);
        } catch (Exception e) {
            log.warn("Gemini call failed for {}: {}", ctx.ticker(), e.getMessage());
            return Optional.empty();
        }
    }
}
