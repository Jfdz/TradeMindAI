package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.annotation.RequiresSubscription;
import com.tradingsaas.tradingcore.adapter.in.web.dto.DeepAnalysisResponse;
import com.tradingsaas.tradingcore.application.usecase.DeepAnalysisService;
import com.tradingsaas.tradingcore.domain.exception.DeepAnalysisUnavailableException;
import com.tradingsaas.tradingcore.domain.exception.SignalNotFoundException;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Premium deep-analysis endpoints for a signal.
 *
 * <p>POST generates (calls ai-engine) and persists; GET returns the stored
 * artifact. Both are gated to {@link SubscriptionPlan#PREMIUM} — a 4-call
 * non-deterministic debate is the most expensive thing a user can trigger, so
 * it never runs on read and never auto-runs per signal.
 */
@RestController
@RequestMapping("/api/v1/signals/{signalId}/deep-analysis")
public class DeepAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(DeepAnalysisController.class);

    private final DeepAnalysisService service;

    public DeepAnalysisController(DeepAnalysisService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresSubscription(SubscriptionPlan.PREMIUM)
    public ResponseEntity<DeepAnalysisResponse> generate(@PathVariable UUID signalId) {
        return ResponseEntity.ok(DeepAnalysisResponse.from(service.generate(signalId)));
    }

    @GetMapping
    @RequiresSubscription(SubscriptionPlan.PREMIUM)
    public ResponseEntity<DeepAnalysisResponse> get(@PathVariable UUID signalId) {
        return service.get(signalId)
                .map(artifact -> ResponseEntity.ok(DeepAnalysisResponse.from(artifact)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(SignalNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSignalNotFound(SignalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "SIGNAL_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(DeepAnalysisUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(DeepAnalysisUnavailableException ex) {
        log.warn("event=deep_analysis.unavailable message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "DEEP_ANALYSIS_UNAVAILABLE", "message", ex.getMessage()));
    }
}
