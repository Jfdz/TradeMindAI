package com.tradingsaas.tradingcore.adapter.out.llm;

import com.tradingsaas.tradingcore.domain.port.out.ReasoningGenerator.ReasoningContext;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class GroqReasoningAdapterTest {

    private MockWebServer server;
    private GroqReasoningAdapter adapter;

    private static final String GROQ_OK =
            "{\"choices\":[{\"message\":{\"content\":\"TSLA surges as delivery beat drives momentum.\"}}]}";
    private static final String GROQ_BANNED =
            "{\"choices\":[{\"message\":{\"content\":\"This is not financial advice but bullish trend noted.\"}}]}";

    @BeforeEach void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        adapter = new GroqReasoningAdapter(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                "test-key", "llama-3.1-8b-instant", 10, 100, 0.7, new ReasoningPromptBuilder());
    }

    @AfterEach void tearDown() throws IOException { server.shutdown(); }

    @Test void returnsTextOnSuccess() {
        server.enqueue(new MockResponse().setBody(GROQ_OK)
                .addHeader("Content-Type", "application/json"));
        Optional<String> result = adapter.generate(
                new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.78), "Tesla delivery beat"));
        assertThat(result).contains("TSLA surges as delivery beat drives momentum.");
    }

    @Test void returnsEmptyOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(503));
        Optional<String> result = adapter.generate(
                new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.78), "news"));
        assertThat(result).isEmpty();
    }

    @Test void returnsEmptyWhenBannedTokenPresent() {
        server.enqueue(new MockResponse().setBody(GROQ_BANNED)
                .addHeader("Content-Type", "application/json"));
        Optional<String> result = adapter.generate(
                new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.78), "news"));
        assertThat(result).isEmpty();
    }

    @Test void lowConfidence_requestBodyContainsCautious() throws InterruptedException {
        server.enqueue(new MockResponse().setBody(GROQ_OK).addHeader("Content-Type", "application/json"));
        adapter.generate(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.30), "news"));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).containsIgnoringCase("cautious");
    }

    @Test void mediumConfidence_requestBodyContainsBalanced() throws InterruptedException {
        server.enqueue(new MockResponse().setBody(GROQ_OK).addHeader("Content-Type", "application/json"));
        adapter.generate(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.55), "news"));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).containsIgnoringCase("balanced");
    }

    @Test void highConfidence_requestBodyContainsHighConviction() throws InterruptedException {
        server.enqueue(new MockResponse().setBody(GROQ_OK).addHeader("Content-Type", "application/json"));
        adapter.generate(new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.80), "news"));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).containsIgnoringCase("high-conviction");
    }

    @Test void skipsHttpCallWhenNotConfigured() {
        GroqReasoningAdapter unconfigured = new GroqReasoningAdapter(
                WebClient.builder().baseUrl(server.url("/").toString()).build(),
                "", "llama-3.1-8b-instant", 10, 100, 0.7, new ReasoningPromptBuilder());
        assertThat(unconfigured.generate(
                new ReasoningContext("TSLA", "BUY", BigDecimal.valueOf(0.78), "news"))).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }
}
