package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.StrategyRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.StrategyResponse;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/strategies")
class StrategyController {

    private final ManageStrategiesUseCase manageStrategiesUseCase;

    StrategyController(ManageStrategiesUseCase manageStrategiesUseCase) {
        this.manageStrategiesUseCase = manageStrategiesUseCase;
    }

    @GetMapping
    Page<StrategyResponse> listStrategies(
            @AuthenticationPrincipal TokenClaims claims,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return manageStrategiesUseCase.getStrategies(claims.userId(), pageable).map(StrategyResponse::fromDomain);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StrategyResponse createStrategy(
            @AuthenticationPrincipal TokenClaims claims,
            @Valid @RequestBody StrategyRequest request) {
        Strategy created = manageStrategiesUseCase.createStrategy(
                claims.userId(),
                claims.subscriptionPlan(),
                request.toCommand()
        );
        return StrategyResponse.fromDomain(created);
    }

    @PutMapping("/{id}")
    StrategyResponse updateStrategy(
            @AuthenticationPrincipal TokenClaims claims,
            @PathVariable UUID id,
            @Valid @RequestBody StrategyRequest request) {
        Strategy updated = manageStrategiesUseCase.updateStrategy(claims.userId(), claims.subscriptionPlan(), id, request.toCommand());
        return StrategyResponse.fromDomain(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteStrategy(@AuthenticationPrincipal TokenClaims claims, @PathVariable UUID id) {
        manageStrategiesUseCase.deleteStrategy(claims.userId(), id);
    }
}
