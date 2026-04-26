package com.tradingsaas.tradingcore.application.usecase.portfolio;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioOverviewService {

    private final PortfolioJpaRepository portfolioJpaRepository;
    private final HistoricalMarketDataPort historicalMarketDataPort;

    public PortfolioOverviewService(PortfolioJpaRepository portfolioJpaRepository,
                                    HistoricalMarketDataPort historicalMarketDataPort) {
        this.portfolioJpaRepository = portfolioJpaRepository;
        this.historicalMarketDataPort = historicalMarketDataPort;
    }

    @Transactional(readOnly = true)
    public PortfolioOverview getOverview(UUID userId, String plan) {
        return portfolioJpaRepository.findByUser_Id(userId)
                .map(this::toOverview)
                .orElseGet(() -> PortfolioOverview.empty(userId, defaultCapitalForPlan(plan)));
    }

    private PortfolioOverview toOverview(PortfolioJpaEntity portfolio) {
        List<PortfolioHoldingOverview> holdings = new ArrayList<>();
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal marketValue = BigDecimal.ZERO;

        for (PortfolioPositionJpaEntity position : portfolio.getPositions()) {
            BigDecimal currentPrice = latestPrice(position.getSymbolTicker()).orElse(position.getEntryPrice());
            BigDecimal positionCost = position.getEntryPrice().multiply(position.getQuantity());
            BigDecimal positionValue = currentPrice.multiply(position.getQuantity());
            BigDecimal pnl = positionValue.subtract(positionCost);

            costBasis = costBasis.add(positionCost);
            marketValue = marketValue.add(positionValue);

            holdings.add(new PortfolioHoldingOverview(
                    position.getSymbolTicker(),
                    position.getQuantity(),
                    position.getEntryPrice(),
                    currentPrice,
                    positionValue,
                    pnl,
                    0.0,
                    position.getStatus(),
                    position.getOpenedAt(),
                    position.getClosedAt()
            ));
        }

        BigDecimal totalMarketValue = marketValue;

        List<PortfolioHoldingOverview> normalizedHoldings = holdings.stream()
                .map(holding -> new PortfolioHoldingOverview(
                        holding.symbol(),
                        holding.quantity(),
                        holding.averageCost(),
                        holding.lastPrice(),
                        holding.marketValue(),
                        holding.unrealizedPnl(),
                        percentage(holding.marketValue(), totalMarketValue),
                        holding.status(),
                        holding.openedAt(),
                        holding.closedAt()
                ))
                .toList();

        BigDecimal cash = portfolio.getInitialCapital().subtract(costBasis);
        BigDecimal unrealizedPnl = marketValue.subtract(costBasis);
        BigDecimal equity = cash.add(marketValue);
        double winRate = normalizedHoldings.isEmpty()
                ? 0
                : (double) normalizedHoldings.stream().filter(h -> h.unrealizedPnl().signum() > 0).count() / normalizedHoldings.size();

        return new PortfolioOverview(
                portfolio.getUser().getId(),
                portfolio.getInitialCapital(),
                cash,
                BigDecimal.ZERO,
                unrealizedPnl,
                equity,
                winRate,
                normalizedHoldings
        );
    }

    private java.util.Optional<BigDecimal> latestPrice(String symbol) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        List<OhlcvBar> bars = historicalMarketDataPort.loadHistoricalBars(symbol, to.minusDays(14), to);
        if (bars.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(BigDecimal.valueOf(bars.get(0).close()));
    }

    private static BigDecimal defaultCapitalForPlan(String plan) {
        return switch (plan == null ? "FREE" : plan.toUpperCase()) {
            case "BASIC" -> BigDecimal.valueOf(25_000);
            case "PREMIUM" -> BigDecimal.valueOf(100_000);
            default -> BigDecimal.valueOf(10_000);
        };
    }

    private static double percentage(BigDecimal value, BigDecimal basis) {
        if (basis == null || basis.signum() == 0) {
            return 0.0;
        }
        return value
                .divide(basis, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
