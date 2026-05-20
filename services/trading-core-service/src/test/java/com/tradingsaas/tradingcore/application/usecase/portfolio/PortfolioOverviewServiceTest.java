package com.tradingsaas.tradingcore.application.usecase.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
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
    void returnsOverviewWithAllPositionsPriced() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

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
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("AAPL", new BigDecimal("110.00"))));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals(userId, overview.userId());
        assertEquals("market-data", overview.dataSource());
        assertEquals(new BigDecimal("220.00"), overview.totalCapital());
        assertEquals(BigDecimal.ZERO, overview.cash());
        assertEquals(0, overview.realizedPnl().compareTo(new BigDecimal("30.00")));
        assertEquals(0, overview.unrealizedPnl().compareTo(new BigDecimal("20.00")));
        assertEquals(0, overview.equity().compareTo(new BigDecimal("220.00")));
        assertEquals(1, overview.holdings().size());
        assertEquals(1, overview.closedPositions().size());
        assertEquals("AAPL", overview.holdings().getFirst().symbol());
        assertEquals("MSFT", overview.closedPositions().getFirst().symbol());
    }

    @Test
    void returnsOverviewWithOneUnpricedSymbol() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        PortfolioPositionJpaEntity aaplPosition = position(
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z"));
        PortfolioPositionJpaEntity nvdaPosition = position(
                portfolio,
                "NVDA",
                new BigDecimal("1"),
                new BigDecimal("500.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-15T10:00:00Z"));
        portfolio.getPositions().add(aaplPosition);
        portfolio.getPositions().add(nvdaPosition);

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL", "NVDA")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("AAPL", new BigDecimal("110.00"))));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals("partial-market-data", overview.dataSource());
        assertEquals(2, overview.holdings().size());

        var aapl = overview.holdings().get(0);
        var nvda = overview.holdings().get(1);

        assertEquals("AAPL", aapl.symbol());
        assertEquals(new BigDecimal("110.00"), aapl.lastPrice());
        assertEquals(new BigDecimal("220.00"), aapl.marketValue());

        assertEquals("NVDA", nvda.symbol());
        assertNull(nvda.lastPrice());
        assertNull(nvda.marketValue());

        assertEquals(0, overview.equity().compareTo(new BigDecimal("220.00")));
        assertEquals(0, overview.unrealizedPnl().compareTo(new BigDecimal("20.00")));
        verify(portfolioRepository, never()).save(any(PortfolioJpaEntity.class));
    }

    @Test
    void persistsPortfolioWhenAllRequestedPricesAreReturned() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("AAPL", new BigDecimal("110.00"))));

        service.getOverview(userId, "premium");

        verify(portfolioRepository, times(1)).save(portfolio);
    }

    @Test
    void marksOverviewUnavailableAndDoesNotPersistZeroWhenPricingFails() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.unavailable("HTTP 401"));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals("unavailable", overview.dataSource());
        assertNull(overview.totalCapital());
        assertNull(overview.equity());
        assertNull(overview.unrealizedPnl());
        assertEquals(1, overview.holdings().size());
        assertNull(overview.holdings().getFirst().lastPrice());
        assertNull(overview.holdings().getFirst().marketValue());
        verify(portfolioRepository, never()).save(any(PortfolioJpaEntity.class));
    }

    @Test
    void returnsEmptyPortfolioWithNoPositions() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals(userId, overview.userId());
        assertNull(overview.totalCapital());
        assertEquals(BigDecimal.ZERO, overview.cash());
        assertEquals(BigDecimal.ZERO, overview.realizedPnl());
        assertNull(overview.equity());
        assertTrue(overview.holdings().isEmpty());
        assertTrue(overview.closedPositions().isEmpty());
    }

    @Test
    void returnsEmptyPortfolioWhenNoPortfolioRow() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals(userId, overview.userId());
        assertNull(overview.totalCapital());
        assertEquals(BigDecimal.ZERO, overview.cash());
        assertEquals(BigDecimal.ZERO, overview.realizedPnl());
        assertNull(overview.equity());
        assertTrue(overview.holdings().isEmpty());
        assertTrue(overview.closedPositions().isEmpty());
    }

    @Test
    void returnsClosedHistoryWhenNoOpenPositionsRemain() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio,
                "NVDA",
                new BigDecimal("4"),
                new BigDecimal("90.00"),
                "CLOSED",
                new BigDecimal("100.00"),
                new BigDecimal("3.00"),
                Instant.parse("2026-04-12T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals("none", overview.dataSource());
        assertTrue(overview.holdings().isEmpty());
        assertEquals(1, overview.closedPositions().size());
        assertEquals("NVDA", overview.closedPositions().getFirst().symbol());
        assertEquals(0, overview.realizedPnl().compareTo(new BigDecimal("37.00")));
    }

    @Test
    void returnsOverviewWithAllPositionsPricedForMultiHoldingPortfolio() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = emptyMarketDataAdapter();
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        PortfolioPositionJpaEntity aaplPosition = position(
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("100.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-16T10:00:00Z"));
        PortfolioPositionJpaEntity msftPosition = position(
                portfolio,
                "MSFT",
                new BigDecimal("3"),
                new BigDecimal("150.00"),
                "OPEN",
                null,
                null,
                Instant.parse("2026-04-15T10:00:00Z"));
        portfolio.getPositions().add(aaplPosition);
        portfolio.getPositions().add(msftPosition);

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL", "MSFT")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of(
                                "AAPL", new BigDecimal("110.00"),
                                "MSFT", new BigDecimal("160.00"))));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals("market-data", overview.dataSource());
        assertEquals(2, overview.holdings().size());

        var aapl = overview.holdings().get(0);
        var msft = overview.holdings().get(1);

        assertEquals("AAPL", aapl.symbol());
        assertEquals(new BigDecimal("110.00"), aapl.lastPrice());
        assertEquals(new BigDecimal("220.00"), aapl.marketValue());
        assertEquals(new BigDecimal("20.00"), aapl.unrealizedPnl());

        assertEquals("MSFT", msft.symbol());
        assertEquals(new BigDecimal("160.00"), msft.lastPrice());
        assertEquals(new BigDecimal("480.00"), msft.marketValue());
        assertEquals(new BigDecimal("30.00"), msft.unrealizedPnl());

        assertEquals(0, overview.totalCapital().compareTo(new BigDecimal("700.00")));
        assertEquals(0, overview.unrealizedPnl().compareTo(new BigDecimal("50.00")));
        assertEquals(0, overview.equity().compareTo(new BigDecimal("700.00")));
    }

    @Test
    void holdingsAreEnrichedWithNameAndSectorFromMarketData() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = Mockito.mock(MarketDataServiceAdapter.class);
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio, "AAPL", new BigDecimal("2"), new BigDecimal("100.00"),
                "OPEN", null, null, Instant.parse("2026-04-16T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("AAPL", new BigDecimal("110.00"))));

        MarketDataServiceAdapter.MarketSymbolPageResponse symbolPage =
                new MarketDataServiceAdapter.MarketSymbolPageResponse(
                        List.of(new MarketDataServiceAdapter.MarketSymbolResponse("AAPL", "Apple Inc.", "NASDAQ", "Technology", true)),
                        0, 50, 1L, 1);
        when(marketDataAdapter.fetchSymbols(anyInt(), anyInt())).thenReturn(symbolPage);
        when(marketDataAdapter.fetchHistoricalPricesBatch(anyList(), anyString(), any(), any(), anyInt()))
                .thenReturn(Map.of());

        PortfolioOverview overview = service.getOverview(userId, "premium");

        PortfolioHoldingOverview holding = overview.holdings().getFirst();
        assertEquals("Apple Inc.", holding.name());
        assertEquals("Technology", holding.sector());
        assertEquals(List.of(), holding.trend7d());
    }

    @Test
    void holdingTrendIsPopulatedFromHistoricalPrices() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = Mockito.mock(MarketDataServiceAdapter.class);
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio, "NVDA", new BigDecimal("1"), new BigDecimal("500.00"),
                "OPEN", null, null, Instant.parse("2026-04-16T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("NVDA")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("NVDA", new BigDecimal("520.00"))));

        when(marketDataAdapter.fetchSymbols(anyInt(), anyInt()))
                .thenReturn(new MarketDataServiceAdapter.MarketSymbolPageResponse(List.of(), 0, 50, 0L, 0));

        MarketDataServiceAdapter.Ohlcv ohlcv = new MarketDataServiceAdapter.Ohlcv(510.0, 525.0, 508.0, 519.0, 100_000L);
        List<MarketDataServiceAdapter.MarketPriceResponse> history = List.of(
                new MarketDataServiceAdapter.MarketPriceResponse("NVDA", LocalDate.of(2026, 5, 5), "DAILY", ohlcv, new BigDecimal("519.00")),
                new MarketDataServiceAdapter.MarketPriceResponse("NVDA", LocalDate.of(2026, 5, 6), "DAILY", ohlcv, new BigDecimal("520.00"))
        );
        when(marketDataAdapter.fetchHistoricalPricesBatch(anyList(), anyString(), any(), any(), anyInt()))
                .thenReturn(Map.of("NVDA", history));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        PortfolioHoldingOverview holding = overview.holdings().getFirst();
        assertEquals(2, holding.trend7d().size());
        assertEquals(new BigDecimal("519.00"), holding.trend7d().get(0));
        assertEquals(new BigDecimal("520.00"), holding.trend7d().get(1));
    }

    @Test
    void enrichmentFailureDoesNotAffectPricingResult() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        HistoricalMarketDataPort marketDataPort = Mockito.mock(HistoricalMarketDataPort.class);
        MarketDataServiceAdapter marketDataAdapter = Mockito.mock(MarketDataServiceAdapter.class);
        PortfolioOverviewService service = new PortfolioOverviewService(portfolioRepository, marketDataPort, marketDataAdapter);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        portfolio.getPositions().add(position(
                portfolio, "AAPL", new BigDecimal("2"), new BigDecimal("100.00"),
                "OPEN", null, null, Instant.parse("2026-04-16T10:00:00Z")));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(marketDataPort.loadLatestPricesResult(List.of("AAPL")))
                .thenReturn(HistoricalMarketDataPort.LatestPricesResult.available(
                        Map.of("AAPL", new BigDecimal("110.00"))));
        when(marketDataAdapter.fetchSymbols(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("enrichment unavailable"));

        PortfolioOverview overview = service.getOverview(userId, "premium");

        assertEquals("market-data", overview.dataSource());
        assertEquals(1, overview.holdings().size());
        PortfolioHoldingOverview holding = overview.holdings().getFirst();
        assertEquals(new BigDecimal("110.00"), holding.lastPrice());
        assertEquals("AAPL", holding.name());
        assertNull(holding.sector());
        assertEquals(List.of(), holding.trend7d());
    }

    private static MarketDataServiceAdapter emptyMarketDataAdapter() {
        MarketDataServiceAdapter adapter = Mockito.mock(MarketDataServiceAdapter.class);
        when(adapter.fetchSymbols(anyInt(), anyInt()))
                .thenReturn(new MarketDataServiceAdapter.MarketSymbolPageResponse(List.of(), 0, 50, 0L, 0));
        when(adapter.fetchHistoricalPricesBatch(anyList(), anyString(), any(), any(), anyInt()))
                .thenReturn(Map.of());
        return adapter;
    }

    private static PortfolioJpaEntity portfolio(UUID userId, BigDecimal totalCapital) {
        UserJpaEntity user = new UserJpaEntity(
                userId,
                "user@example.com",
                null,
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
                totalCapital,
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
