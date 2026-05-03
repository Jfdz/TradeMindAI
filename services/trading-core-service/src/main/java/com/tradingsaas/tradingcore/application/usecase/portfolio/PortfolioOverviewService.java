package com.tradingsaas.tradingcore.application.usecase.portfolio;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort.LatestPricesResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioOverviewService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOverviewService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PortfolioJpaRepository portfolioJpaRepository;
    private final HistoricalMarketDataPort historicalMarketDataPort;

    public PortfolioOverviewService(PortfolioJpaRepository portfolioJpaRepository,
                                    HistoricalMarketDataPort historicalMarketDataPort) {
        this.portfolioJpaRepository = portfolioJpaRepository;
        this.historicalMarketDataPort = historicalMarketDataPort;
    }

    @Transactional
    public PortfolioOverview getOverview(UUID userId, String plan) {
        return portfolioJpaRepository.findByUser_Id(userId)
                .map(this::toOverview)
                .orElseGet(() -> PortfolioOverview.empty(userId, false));
    }

    private PortfolioOverview toOverview(PortfolioJpaEntity portfolio) {
        List<PortfolioPositionJpaEntity> openPositions = portfolio.getPositions().stream()
                .filter(p -> !"CLOSED".equals(p.getStatus()))
                .toList();

        if (openPositions.isEmpty()) {
            return PortfolioOverview.empty(portfolio.getUser().getId(), true);
        }

        List<String> tickers = openPositions.stream()
                .map(PortfolioPositionJpaEntity::getSymbolTicker)
                .distinct()
                .toList();

        LatestPricesResult latestPricesResult = historicalMarketDataPort.loadLatestPricesResult(tickers);
        if (!latestPricesResult.available()) {
            log.warn("Portfolio pricing unavailable for userId={} tickers={} reason={}",
                    portfolio.getUser().getId(), tickers, latestPricesResult.reason());
        }
        Map<String, BigDecimal> latestPrices = latestPricesResult.prices();
        Set<String> returnedTickers = new LinkedHashSet<>(latestPrices.keySet());
        Set<String> missingTickers = tickers.stream()
                .filter(ticker -> !returnedTickers.contains(ticker))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (latestPricesResult.available()) {
            log.info("Portfolio pricing lookup userId={} requestedTickers={} returnedTickers={} missingTickers={}",
                    portfolio.getUser().getId(), tickers, returnedTickers, missingTickers);
        }

        List<PortfolioHoldingOverview> holdings = new ArrayList<>();
        BigDecimal pricedCostBasis = ZERO;
        BigDecimal totalMarketValue = ZERO;
        int pricedPositionCount = 0;

        for (PortfolioPositionJpaEntity position : openPositions) {
            BigDecimal quantity = position.getQuantity();
            BigDecimal entryPrice = position.getEntryPrice();
            BigDecimal positionCost = entryPrice.multiply(quantity);

            BigDecimal currentPrice = latestPrices.get(position.getSymbolTicker());
            BigDecimal positionValue = (currentPrice != null) ? currentPrice.multiply(quantity) : null;
            BigDecimal pnl = (positionValue != null) ? positionValue.subtract(positionCost) : null;

            if (positionValue != null && currentPrice != null) {
                totalMarketValue = totalMarketValue.add(positionValue);
                pricedCostBasis = pricedCostBasis.add(positionCost);
                pricedPositionCount++;
            }
            holdings.add(new PortfolioHoldingOverview(
                    position.getSymbolTicker(),
                    quantity,
                    entryPrice,
                    currentPrice,
                    positionValue,
                    pnl,
                    0.0,
                    position.getStatus(),
                    position.getOpenedAt(),
                    position.getClosedAt()
            ));
        }

        final BigDecimal finalTotalMarketValue = totalMarketValue;
        List<PortfolioHoldingOverview> normalizedHoldings = holdings.stream()
                .map(holding -> {
                    BigDecimal mv = holding.marketValue();
                    Double pct = (finalTotalMarketValue != null && finalTotalMarketValue.signum() > 0 && mv != null)
                            ? percentage(mv, finalTotalMarketValue)
                            : 0.0;
                    return new PortfolioHoldingOverview(
                            holding.symbol(),
                            holding.quantity(),
                            holding.averageCost(),
                            holding.lastPrice(),
                            holding.marketValue(),
                            holding.unrealizedPnl(),
                            pct,
                            holding.status(),
                            holding.openedAt(),
                            holding.closedAt()
                    );
                })
                .toList();

        BigDecimal unrealizedPnl = (pricedPositionCount > 0)
                ? totalMarketValue.subtract(pricedCostBasis)
                : ZERO;

        long pricedHoldingCount = normalizedHoldings.stream()
                .filter(h -> h.unrealizedPnl() != null)
                .count();

        double winRate = pricedHoldingCount == 0
                ? 0
                : (double) normalizedHoldings.stream()
                        .filter(h -> h.unrealizedPnl() != null && h.unrealizedPnl().signum() > 0)
                        .count() / pricedHoldingCount;

        BigDecimal realizedPnl = portfolio.getPositions().stream()
                .filter(p -> "CLOSED".equals(p.getStatus()) && p.getExitPrice() != null)
                .map(p -> p.getExitPrice()
                        .subtract(p.getEntryPrice())
                        .multiply(p.getQuantity())
                        .subtract(p.getFees()))
                .reduce(ZERO, BigDecimal::add);

        BigDecimal equity = (pricedPositionCount > 0) ? totalMarketValue : ZERO;
        String dataSource = resolveDataSource(latestPricesResult, missingTickers);

        // Only persist when all requested prices were available and at least one position was priced.
        if ("market-data".equals(dataSource) && pricedPositionCount > 0) {
            portfolio.setTotalCapital(totalMarketValue);
            portfolioJpaRepository.save(portfolio);
        }

        return new PortfolioOverview(
                portfolio.getUser().getId(),
                totalMarketValue,
                ZERO,
                realizedPnl,
                unrealizedPnl,
                equity,
                winRate,
                dataSource,
                normalizedHoldings
        );
    }

    private static String resolveDataSource(LatestPricesResult latestPricesResult, Set<String> missingTickers) {
        if (!latestPricesResult.available()) {
            return "unavailable";
        }
        if (!missingTickers.isEmpty()) {
            return "partial-market-data";
        }
        return "market-data";
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
