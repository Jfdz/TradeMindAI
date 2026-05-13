package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Wire shape posted by ai-engine to
 * {@code PUT /api/v1/internal/signals/{signalId}/reasoning}.
 *
 * <p>All artifact subfields are optional individually — only {@code outcome}
 * is required — so the same wire format covers GENERATED, REFUSED_BY_LLM,
 * REFUSED_BY_VALIDATOR, REFUSED_LLM_DISABLED, REFUSED_NO_FACTS, and ERROR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateReasoningRequest(
        String reasoning,
        String reasoningStatus,
        String reasoningGeneratedAt,
        String outcome,
        String provider,
        String modelVersion,
        Integer retryCount,
        String refusalReason,
        Map<String, Object> factsSnapshot,
        List<String> priceRefs,
        List<String> newsRefs,
        List<Map<String, Object>> validatorViolations,
        Map<String, Object> rawAudit) {}
