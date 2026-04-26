package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioPositionJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase;
import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase.AddPositionCommand;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioHoldingOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverviewService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class PortfolioControllerTest {

    @Test
    void mapsPortfolioOverviewToResponse() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        AddPortfolioPositionUseCase addPositionUseCase = mock(AddPortfolioPositionUseCase.class);
        PortfolioPositionJpaRepository positionRepository = mock(PortfolioPositionJpaRepository.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, positionRepository);

        PortfolioOverview overview = new PortfolioOverview(
                userId,
                new BigDecimal("10000"),
                new BigDecimal("9800"),
                new BigDecimal("30"),
                new BigDecimal("20"),
                new BigDecimal("10020"),
                1.0,
                List.of(new PortfolioHoldingOverview(
                        "AAPL",
                        new BigDecimal("2"),
                        new BigDecimal("100"),
                        new BigDecimal("110"),
                        new BigDecimal("220"),
                        new BigDecimal("20"),
                        100.0,
                        "OPEN",
                        Instant.parse("2026-04-16T10:00:00Z"),
                        null)));
        when(overviewService.getOverview(userId, "PREMIUM")).thenReturn(overview);

        PortfolioController.PortfolioOverviewResponse response =
                controller.getPortfolio(auth(userId, "PREMIUM"));

        assertEquals(userId, response.userId());
        assertEquals(1, response.holdings().size());
        assertEquals("AAPL", response.holdings().getFirst().symbol());
        assertEquals(0, response.realizedPnl().compareTo(new BigDecimal("30")));
    }

    @Test
    void addPositionDefaultsMissingFeesToZero() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        CapturingAddPositionUseCase addPositionUseCase = new CapturingAddPositionUseCase();
        PortfolioPositionJpaRepository positionRepository = mock(PortfolioPositionJpaRepository.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, positionRepository);

        Map<String, UUID> response = controller.addPosition(
                new PortfolioController.AddPositionRequest(
                        "AAPL",
                        new BigDecimal("1"),
                        new BigDecimal("150.00"),
                        LocalDate.of(2026, 4, 1),
                        null,
                        "note"),
                auth(userId, "BASIC"));

        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), response.get("id"));
        assertEquals(userId, addPositionUseCase.lastCommand.userId());
        assertEquals("BASIC", addPositionUseCase.lastCommand.subscriptionPlan());
        assertEquals(0, addPositionUseCase.lastCommand.fees().compareTo(BigDecimal.ZERO));
    }

    @Test
    void updateAndClosePositionMutateTheEntity() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        AddPortfolioPositionUseCase addPositionUseCase = mock(AddPortfolioPositionUseCase.class);
        PortfolioPositionJpaRepository positionRepository = mock(PortfolioPositionJpaRepository.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, positionRepository);

        PortfolioPositionJpaEntity position = position(userId, "AAPL", "OPEN");
        when(positionRepository.findByIdAndUserId(position.getId(), userId)).thenReturn(Optional.of(position));

        controller.updatePosition(
                position.getId(),
                new PortfolioController.UpdatePositionRequest(
                        new BigDecimal("3"),
                        new BigDecimal("160.00"),
                        LocalDate.of(2026, 4, 2),
                        new BigDecimal("2.00"),
                        "updated"),
                auth(userId, "FREE"));

        controller.closePosition(
                position.getId(),
                new PortfolioController.ClosePositionRequest(
                        new BigDecimal("175.00"),
                        Instant.parse("2026-04-20T10:00:00Z"),
                        new BigDecimal("1.50")),
                auth(userId, "FREE"));

        verify(positionRepository, org.mockito.Mockito.atLeastOnce()).save(position);
        assertEquals(0, position.getQuantity().compareTo(new BigDecimal("3")));
        assertEquals(0, position.getEntryPrice().compareTo(new BigDecimal("160.00")));
        assertEquals("CLOSED", position.getStatus());
        assertEquals(0, position.getExitPrice().compareTo(new BigDecimal("175.00")));
        assertEquals(0, position.getFees().compareTo(new BigDecimal("3.50")));
    }

    @Test
    void rejectsClosedPositionsOnUpdate() {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        AddPortfolioPositionUseCase addPositionUseCase = mock(AddPortfolioPositionUseCase.class);
        PortfolioPositionJpaRepository positionRepository = mock(PortfolioPositionJpaRepository.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, positionRepository);

        PortfolioPositionJpaEntity position = position(userId, "AAPL", "CLOSED");
        when(positionRepository.findByIdAndUserId(position.getId(), userId)).thenReturn(Optional.of(position));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.updatePosition(
                        position.getId(),
                        new PortfolioController.UpdatePositionRequest(
                                new BigDecimal("3"),
                                new BigDecimal("160.00"),
                                LocalDate.of(2026, 4, 2),
                                new BigDecimal("2.00"),
                                "updated"),
                        auth(userId, "FREE")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void deletesOpenPosition() {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        AddPortfolioPositionUseCase addPositionUseCase = mock(AddPortfolioPositionUseCase.class);
        PortfolioPositionJpaRepository positionRepository = mock(PortfolioPositionJpaRepository.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, positionRepository);

        PortfolioPositionJpaEntity position = position(userId, "AAPL", "OPEN");
        when(positionRepository.findByIdAndUserId(position.getId(), userId)).thenReturn(Optional.of(position));

        controller.deletePosition(position.getId(), auth(userId, "FREE"));

        verify(positionRepository).delete(position);
    }

    private static Authentication auth(UUID userId, String plan) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new TokenClaims(userId, "user@example.com", plan));
        return authentication;
    }

    private static PortfolioPositionJpaEntity position(UUID userId, String ticker, String status) {
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
        PortfolioJpaEntity portfolio = new PortfolioJpaEntity(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                user,
                new BigDecimal("10000"),
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        return new PortfolioPositionJpaEntity(
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                portfolio,
                ticker,
                new BigDecimal("2"),
                new BigDecimal("150.00"),
                new BigDecimal("1.00"),
                "notes",
                LocalDate.of(2026, 4, 1),
                status,
                Instant.parse("2026-04-02T10:00:00Z"));
    }

    private static final class CapturingAddPositionUseCase implements AddPortfolioPositionUseCase {
        private AddPositionCommand lastCommand;

        @Override
        public UUID addPosition(AddPositionCommand command) {
            this.lastCommand = command;
            return UUID.fromString("33333333-3333-3333-3333-333333333333");
        }
    }
}
