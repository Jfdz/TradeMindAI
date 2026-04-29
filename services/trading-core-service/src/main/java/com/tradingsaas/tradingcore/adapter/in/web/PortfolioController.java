package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase;
import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase.AddPositionCommand;
import com.tradingsaas.tradingcore.application.usecase.portfolio.ManagePortfolioPositionUseCase;
import com.tradingsaas.tradingcore.application.usecase.portfolio.ManagePortfolioPositionUseCase.CloseCommand;
import com.tradingsaas.tradingcore.application.usecase.portfolio.ManagePortfolioPositionUseCase.UpdateCommand;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioHoldingOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverviewService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/portfolio")
class PortfolioController {

    private final PortfolioOverviewService portfolioOverviewService;
    private final AddPortfolioPositionUseCase addPositionUseCase;
    private final ManagePortfolioPositionUseCase managePositionUseCase;

    PortfolioController(PortfolioOverviewService portfolioOverviewService,
                        AddPortfolioPositionUseCase addPositionUseCase,
                        ManagePortfolioPositionUseCase managePositionUseCase) {
        this.portfolioOverviewService = portfolioOverviewService;
        this.addPositionUseCase = addPositionUseCase;
        this.managePositionUseCase = managePositionUseCase;
    }

    @GetMapping
    PortfolioOverviewResponse getPortfolio(Authentication authentication) {
        TokenClaims claims = claims(authentication);
        return PortfolioOverviewResponse.from(
                portfolioOverviewService.getOverview(claims.userId(), claims.subscriptionPlan())
        );
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> addPosition(@Valid @RequestBody AddPositionRequest body, Authentication authentication) {
        TokenClaims claims = claims(authentication);
        UUID positionId = addPositionUseCase.addPosition(new AddPositionCommand(
                claims.userId(),
                claims.subscriptionPlan(),
                body.ticker(),
                body.quantity(),
                body.entryPrice(),
                body.purchaseDate(),
                body.fees() != null ? body.fees() : BigDecimal.ZERO,
                body.notes()
        ));
        return Map.of("id", positionId);
    }

    @PutMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.OK)
    void updatePosition(@PathVariable UUID id,
                        @Valid @RequestBody UpdatePositionRequest body,
                        Authentication authentication) {
        TokenClaims claims = claims(authentication);
        managePositionUseCase.update(new UpdateCommand(
                id, claims.userId(), body.quantity(), body.entryPrice(),
                body.fees(), body.notes(), body.purchaseDate()));
    }

    @DeleteMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePosition(@PathVariable UUID id, Authentication authentication) {
        TokenClaims claims = claims(authentication);
        managePositionUseCase.delete(id, claims.userId());
    }

    @PostMapping("/positions/{id}/close")
    @ResponseStatus(HttpStatus.OK)
    void closePosition(@PathVariable UUID id,
                       @Valid @RequestBody ClosePositionRequest body,
                       Authentication authentication) {
        TokenClaims claims = claims(authentication);
        managePositionUseCase.close(new CloseCommand(id, claims.userId(),
                body.exitPrice(), body.fees(), body.closedAt()));
    }

    // ── request DTOs ─────────────────────────────────────────────────────────

    record AddPositionRequest(
            @NotBlank String ticker,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @DecimalMin("0.0001") BigDecimal entryPrice,
            LocalDate purchaseDate,
            BigDecimal fees,
            String notes) {}

    record UpdatePositionRequest(
            @NotNull @Positive BigDecimal quantity,
            @NotNull @DecimalMin("0.0001") BigDecimal entryPrice,
            LocalDate purchaseDate,
            BigDecimal fees,
            String notes) {}

    record ClosePositionRequest(
            @NotNull @DecimalMin("0.0001") BigDecimal exitPrice,
            Instant closedAt,
            BigDecimal fees) {}

    // ── response DTOs ────────────────────────────────────────────────────────

    record PortfolioOverviewResponse(
            UUID userId,
            BigDecimal initialCapital,
            BigDecimal cash,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal equity,
            double winRate,
            List<PortfolioHoldingResponse> holdings) {

        static PortfolioOverviewResponse from(PortfolioOverview overview) {
            return new PortfolioOverviewResponse(
                    overview.userId(),
                    overview.initialCapital(),
                    overview.cash(),
                    overview.realizedPnl(),
                    overview.unrealizedPnl(),
                    overview.equity(),
                    overview.winRate(),
                    overview.holdings().stream().map(PortfolioHoldingResponse::from).toList()
            );
        }
    }

    record PortfolioHoldingResponse(
            String symbol,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal lastPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl,
            double allocationPct,
            String status,
            Instant openedAt,
            Instant closedAt) {

        static PortfolioHoldingResponse from(PortfolioHoldingOverview overview) {
            return new PortfolioHoldingResponse(
                    overview.symbol(),
                    overview.quantity(),
                    overview.averageCost(),
                    overview.lastPrice(),
                    overview.marketValue(),
                    overview.unrealizedPnl(),
                    overview.allocationPct(),
                    overview.status(),
                    overview.openedAt(),
                    overview.closedAt()
            );
        }
    }

    private TokenClaims claims(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof TokenClaims tokenClaims) {
            return tokenClaims;
        }
        throw new IllegalStateException("Invalid authentication principal");
    }
}
