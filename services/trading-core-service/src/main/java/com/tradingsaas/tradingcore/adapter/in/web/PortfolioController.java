package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioHoldingOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverviewService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioController {

    private final PortfolioOverviewService portfolioOverviewService;

    PortfolioController(PortfolioOverviewService portfolioOverviewService) {
        this.portfolioOverviewService = portfolioOverviewService;
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
    PositionResponse addPosition(@Valid @RequestBody AddPositionRequest request, Authentication authentication) {
        TokenClaims claims = claims(authentication);
        var saved = portfolioOverviewService.addPosition(
                claims.userId(),
                request.ticker(),
                request.quantity(),
                request.entryPrice()
        );
        return new PositionResponse(saved.getId(), saved.getSymbolTicker(), saved.getQuantity(),
                saved.getEntryPrice(), saved.getStatus(), saved.getOpenedAt());
    }

    @DeleteMapping("/positions/{positionId}")
    ResponseEntity<Void> deletePosition(@PathVariable UUID positionId, Authentication authentication) {
        TokenClaims claims = claims(authentication);
        try {
            portfolioOverviewService.deletePosition(positionId, claims.userId());
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private TokenClaims claims(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof TokenClaims tokenClaims) {
            return tokenClaims;
        }
        throw new IllegalStateException("Invalid authentication principal");
    }

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

    record AddPositionRequest(
            @NotBlank String ticker,
            @NotNull @DecimalMin("0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin("0.01") BigDecimal entryPrice) {}

    record PositionResponse(UUID id, String ticker, BigDecimal quantity, BigDecimal entryPrice,
                            String status, Instant openedAt) {}

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
}
