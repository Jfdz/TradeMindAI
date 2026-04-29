package com.tradingsaas.tradingcore.application.usecase.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PortfolioOverviewServiceTest {

    @Test
    void returnsOverviewWithRealizedAndUnrealizedPnl() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        PortfolioPositionJpaEntity openPosition = position(
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z"));
        PortfolioPositionJpaEntity closedPosition = position(
                portfolio,
                "MSFT",
                new BigDecimal("1"),
                new BigDecimal("120.00"),
                "CLOSED",
                new BigDecimal("155.00"),
                new BigDecimal("5.00"),
                Instant.parse("2026-04-10T10:00:00Z"));
        portfolio.getPositions().add(openPosition);
        portfolio.getPositions().add(closedPosition);

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", new BigDecimal("110.00")));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals(userId, overview.userId());
        assertEquals(0, overview.initialCapital().compareTo(new BigDecimal("10000")));
        assertEquals(0, overview.cash().compareTo(new BigDecimal("9800.00")));
        assertEquals(0, overview.realizedPnl().compareTo(new BigDecimal("30.00")));
        assertEquals(0, overview.unrealizedPnl().compareTo(new BigDecimal("20.00")));
        assertEquals(0, overview.equity().compareTo(new BigDecimal("10020.00")));
        assertEquals(1, overview.holdings().size());
        assertEquals("AAPL", overview.holdings().getFirst().symbol());
        assertEquals(100.0, overview.holdings().getFirst().allocationPct(), 0.0001);

        verify(marketDataPort).loadLatestPrices(List.of("AAPL"));
        verify(marketDataPort, never()).loadHistoricalBars(Mockito.anyString(), Mockito.any(), Mockito.any());
    }

    @Test
    void returnsDefaultCapitalWhenPortfolioMissing() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort);

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals(userId, overview.userId());
        assertEquals(0, overview.initialCapital().compareTo(new BigDecimal("100000")));
        assertEquals(0, overview.cash().compareTo(new BigDecimal("100000")));
        assertEquals(BigDecimal.ZERO, overview.realizedPnl());
        assertTrue(overview.holdings().isEmpty());
    }

    private static PortfolioJpaEntity portfolio(UUID userId, BigDecimal initialCapital) {
        UserJpaEntity user = new UserJpaEntity(
                userId,
                "user@example.com",
                "$2a$10$hash",
                "Test",
                "User",
                "UTC",
                true,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        return new PortfolioJpaEntity(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                user,
                initialCapital,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
    }

    private static PortfolioPositionJpaEntity position(
            PortfolioJpaEntity portfolio,
            String ticker,
            BigDecimal quantity,
            BigDecimal entryPrice,
            String status,
            BigDecimal exitPrice,
            BigDecimal fees,
            Instant openedAt) {
        BigDecimal openingFees = exitPrice != null ? BigDecimal.ZERO : fees;
        PortfolioPositionJpaEntity position = new PortfolioPositionJpaEntity(
                UUID.randomUUID(),
                portfolio,
                ticker,
                quantity,
                entryPrice,
                openingFees,
                "notes",
                LocalDate.of(2026, 4, 1),
                status,
                openedAt);
        if (exitPrice != null) {
            position.close(exitPrice, fees, Instant.parse("2026-04-20T10:00:00Z"));
        }
        return position;
    }
}
