package com.tradingsaas.tradingcore.adapter.out.aiengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisArtifact;
import com.tradingsaas.tradingcore.domain.model.DeepAnalysisSignalFacts;
import com.tradingsaas.tradingcore.domain.port.out.DeepAnalysisEnginePort;
import io.netty.channel.ChannelOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Calls ai-engine's compute-only deep-analysis endpoint
 * ({@code POST /api/v1/internal/deep-analysis/{ticker}}) and maps the response
 * to a {@link DeepAnalysisArtifact}.
 *
 * <p>The debate runs four sequential LLM calls, so the response timeout is
 * generous (default 120s). Any non-2xx (ai-engine returns 422 for no facts,
 * 502 for no verdict) and any transport failure surface as
 * {@link DeepAnalysisUnavailableException} so the orchestrating service never
 * persists a non-artifact.
 */
@Component
public class AiEngineDeepAnalysisAdapter implements DeepAnalysisEnginePort {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final WebClient webClient;
    private final String internalSecret;

    @Autowired
    public AiEngineDeepAnalysisAdapter(
            @Value("${services.ai-engine.url:http://localhost:8000}") String baseUrl,
            @Value("${services.ai-engine.internal-secret:}") String internalSecret,
            @Value("${services.ai-engine.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${services.ai-engine.response-timeout-seconds:120}") int responseTimeoutSeconds) {
        this(
                WebClient.builder()
                        .baseUrl(baseUrl)
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))))
                        .build(),
                internalSecret);
    }

    AiEngineDeepAnalysisAdapter(WebClient webClient, String internalSecret) {
        this.webClient = webClient;
        this.internalSecret = internalSecret;
    }

    @Override
    public DeepAnalysisArtifact generate(DeepAnalysisSignalFacts facts) {
        try {
            DeepAnalysisResponse response = webClient.post()
                    .uri("/api/v1/internal/deep-analysis/{ticker}", facts.ticker())
                    .headers(this::addInternalSecret)
                    .bodyValue(new RequestBody(
                            facts.signalType(),
                            facts.confidence(),
                            facts.entryPrice(),
                            facts.predictedChangePct(),
                            facts.targetPrice(),
                            facts.stopLoss(),
                            facts.expectedMovePct(),
                            facts.generatedAt()))
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            res -> Mono.error(new DeepAnalysisUnavailableException(
                                    "ai-engine deep-analysis returned " + res.statusCode())))
                    .bodyToMono(DeepAnalysisResponse.class)
                    .block();

            if (response == null) {
                throw new DeepAnalysisUnavailableException("ai-engine deep-analysis returned empty body");
            }
            return toArtifact(response);
        } catch (DeepAnalysisUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DeepAnalysisUnavailableException(
                    "ai-engine deep-analysis call failed: " + e.getMessage());
        }
    }

    private void addInternalSecret(HttpHeaders headers) {
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.set(INTERNAL_SECRET_HEADER, internalSecret);
        }
    }

    private static DeepAnalysisArtifact toArtifact(DeepAnalysisResponse r) {
        return new DeepAnalysisArtifact(
                r.schemaVersion(),
                r.outcome(),
                r.ticker(),
                r.signalType(),
                r.verdictDirection(),
                r.conviction(),
                toSection(r.verdict()),
                r.sections() == null
                        ? List.of()
                        : r.sections().stream().map(AiEngineDeepAnalysisAdapter::toSection).toList(),
                r.provider(),
                r.modelVersion(),
                r.generatedAt());
    }

    private static DeepAnalysisArtifact.Section toSection(SectionResponse s) {
        if (s == null) {
            return null;
        }
        return new DeepAnalysisArtifact.Section(
                s.role(),
                s.text(),
                s.priceRefs() == null ? List.of() : s.priceRefs(),
                s.newsRefs() == null ? List.of() : s.newsRefs(),
                s.refused(),
                s.refusalReason(),
                s.validatorViolations() == null ? List.of() : s.validatorViolations());
    }

    record RequestBody(
            String signalType,
            BigDecimal confidence,
            BigDecimal entryPrice,
            BigDecimal predictedChangePct,
            BigDecimal targetPrice,
            BigDecimal stopLoss,
            BigDecimal expectedMovePct,
            Instant generatedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeepAnalysisResponse(
            String schemaVersion,
            String outcome,
            String ticker,
            String signalType,
            Instant generatedAt,
            String verdictDirection,
            String conviction,
            SectionResponse verdict,
            List<SectionResponse> sections,
            String provider,
            String modelVersion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SectionResponse(
            String role,
            String text,
            List<String> priceRefs,
            List<String> newsRefs,
            boolean refused,
            String refusalReason,
            List<Map<String, Object>> validatorViolations) {}
}
