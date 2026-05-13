package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningAuditResponse;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only view of the C5 reasoning artifact for a single signal.
 *
 * <p>Returns the full audit blob (price-facts snapshot, news refs, validator
 * violations, raw provider response) so an operator can answer "why did
 * this signal produce that reasoning text?" by replaying the inputs.
 *
 * <p>Guarded by {@code .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")}
 * in {@code SecurityConfig}. JWT-authenticated; the InternalSecretFilter
 * does not apply on this path.
 */
@RestController
@RequestMapping("/api/v1/admin/signals")
public class AdminSignalReasoningController {

    private final TradingSignalRepository repository;

    public AdminSignalReasoningController(TradingSignalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @GetMapping("/{signalId}/reasoning-audit")
    public ResponseEntity<ReasoningAuditResponse> getReasoningAudit(@PathVariable UUID signalId) {
        return repository.findById(signalId)
                .map(ReasoningAuditResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
