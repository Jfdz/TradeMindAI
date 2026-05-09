package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiReasoningAdapterTest {

    private MockWebServer server;
    private GeminiReasoningAdapter adapter;

    private static final String GEMINI_OK =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"AAPL clears 200-day MA on strong iPhone demand.\"}]}}]}";
    private static final String GEMINI_BANNED =
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"As an AI model I see bullish trend.\"}]}}]}";

    @BeforeEach void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        adapter = new GeminiReasoningAdapter(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                "test-key", 10, 100, 0.7);
    }

    @AfterEach void tearDown() throws IOException { server.shutdown(); }

    @Test void returnsTextOnSuccess() {
        server.enqueue(new MockResponse().setBody(GEMINI_OK)
                .addHeader("Content-Type", "application/json"));
        Optional<String> result = adapter.generate(
                new ReasoningContext("AAPL", "BUY", BigDecimal.valueOf(0.85), "Apple reports record sales"));
        assertThat(result).contains("AAPL clears 200-day MA on strong iPhone demand.");
    }

    @Test void returnsEmptyOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(429));
        Optional<String> result = adapter.generate(
                new ReasoningContext("AAPL", "BUY", BigDecimal.valueOf(0.85), "news"));
        assertThat(result).isEmpty();
    }

    @Test void returnsEmptyWhenBannedTokenPresent() {
        server.enqueue(new MockResponse().setBody(GEMINI_BANNED)
                .addHeader("Content-Type", "application/json"));
        Optional<String> result = adapter.generate(
                new ReasoningContext("AAPL", "BUY", BigDecimal.valueOf(0.85), "news"));
        assertThat(result).isEmpty();
    }

    @Test void skipsHttpCallWhenNotConfigured() {
        GeminiReasoningAdapter unconfigured = new GeminiReasoningAdapter(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                "", 10, 100, 0.7);
        assertThat(unconfigured.generate(
                new ReasoningContext("AAPL", "BUY", BigDecimal.valueOf(0.85), "news"))).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }
}
