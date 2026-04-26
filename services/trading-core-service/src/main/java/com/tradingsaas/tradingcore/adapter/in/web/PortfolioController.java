package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioHoldingOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverviewService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
