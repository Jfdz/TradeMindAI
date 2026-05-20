package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.UpdateReasoningRequest;
import com.tradingsaas.tradingcore.domain.model.ReasoningArtifact;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.port.in.UpdateSignalReasoningUseCase;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoint consumed by ai-engine after the validated
 * reasoning pipeline (C3c + C4 + C5) finishes. Guarded by the existing
 * {@code InternalSecretFilter} via the {@code /api/v1/internal/**} prefix
 * — no user JWT required.
 *
 * <p>Idempotent: posting the same artifact twice yields the same row
 * state (latest write wins), so ai-engine can retry on transport
 * failures without producing audit duplicates.
 */
@RestController
@RequestMapping("/api/v1/internal/signals")
public class SignalReasoningController {

    private static final Logger log = LoggerFactory.getLogger(SignalReasoningController.class);

    private final UpdateSignalReasoningUseCase useCase;

    public SignalReasoningController(UpdateSignalReasoningUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    }

    @PutMapping("/{signalId}/reasoning")
    public ResponseEntity<?> updateReasoning(
            @PathVariable UUID signalId,
            @RequestBody UpdateReasoningRequest request) {

        if (request == null || request.outcome() == null || request.outcome().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "outcome is required"));
        }

        ReasoningStatus status;
        try {
            status = parseStatus(request.reasoningStatus(), request.outcome());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid reasoningStatus: " + e.getMessage()));
        }

        Instant generatedAt;
        try {
            generatedAt = request.reasoningGeneratedAt() == null
                    ? Instant.now()
                    : Instant.parse(request.reasoningGeneratedAt());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid reasoningGeneratedAt: must be ISO-8601 instant"));
        }

        ReasoningArtifact artifact = new ReasoningArtifact(
                request.outcome(),
                request.provider(),
                request.modelVersion(),
                request.retryCount() != null ? request.retryCount() : 0,
                request.refusalReason(),
                request.factsSnapshot(),
                request.priceRefs(),
                request.newsRefs(),
                request.validatorViolations(),
                request.rawAudit());

        UpdateSignalReasoningUseCase.Outcome outcome = useCase.execute(
                signalId, request.reasoning(), status, generatedAt, artifact);

        if (outcome == UpdateSignalReasoningUseCase.Outcome.SIGNAL_NOT_FOUND) {
            log.info("event=signal_reasoning.not_found signal_id={}", signalId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Maps the wire-level {@code reasoningStatus} (if supplied) to the
     * enum, falling back to inference from {@code outcome} when ai-engine
     * does not send it explicitly. The fine-grained ai-engine outcome is
     * preserved verbatim in the artifact regardless.
     *
     * <p>Inference mapping:
     * <ul>
     *   <li>GENERATED                            → READY</li>
     *   <li>REFUSED_LLM_DISABLED / REFUSED_NO_FACTS → FALLBACK (UI shows template)</li>
     *   <li>REFUSED_BY_LLM / REFUSED_BY_VALIDATOR / ERROR → FAILED</li>
     * </ul>
     */
    private ReasoningStatus parseStatus(String rawStatus, String outcome) {
        if (rawStatus != null && !rawStatus.isBlank()) {
            return ReasoningStatus.valueOf(rawStatus.toUpperCase());
        }
        return switch (outcome.toUpperCase()) {
            case "GENERATED" -> ReasoningStatus.READY;
            case "REFUSED_LLM_DISABLED", "REFUSED_NO_FACTS" -> ReasoningStatus.FALLBACK;
            default -> ReasoningStatus.FAILED;
        };
    }
}
