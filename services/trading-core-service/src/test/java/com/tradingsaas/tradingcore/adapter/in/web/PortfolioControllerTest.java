package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase;
import com.tradingsaas.tradingcore.application.usecase.portfolio.AddPortfolioPositionUseCase.AddPositionCommand;
import com.tradingsaas.tradingcore.application.usecase.portfolio.ManagePortfolioPositionUseCase;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioHoldingOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverview;
import com.tradingsaas.tradingcore.application.usecase.portfolio.PortfolioOverviewService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class PortfolioControllerTest {

    @Test
    void mapsPortfolioOverviewToResponse() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        AddPortfolioPositionUseCase addPositionUseCase = mock(AddPortfolioPositionUseCase.class);
        ManagePortfolioPositionUseCase managePositionUseCase = mock(ManagePortfolioPositionUseCase.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, managePositionUseCase);

        when(overviewService.getOverview(userId, "PREMIUM")).thenReturn(new PortfolioOverview(
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
                        null))));

        PortfolioController.PortfolioOverviewResponse response = controller.getPortfolio(auth(userId, "PREMIUM"));

        assertEquals(userId, response.userId());
        assertEquals(1, response.holdings().size());
        assertEquals("AAPL", response.holdings().getFirst().symbol());
    }

    @Test
    void addPositionDefaultsMissingFeesToZero() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioOverviewService overviewService = mock(PortfolioOverviewService.class);
        CapturingAddPositionUseCase addPositionUseCase = new CapturingAddPositionUseCase();
        ManagePortfolioPositionUseCase managePositionUseCase = mock(ManagePortfolioPositionUseCase.class);
        PortfolioController controller = new PortfolioController(overviewService, addPositionUseCase, managePositionUseCase);

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
    void updateCloseAndDeleteDelegateToManageUseCase() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID positionId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        PortfolioController controller = new PortfolioController(
                mock(PortfolioOverviewService.class),
                mock(AddPortfolioPositionUseCase.class),
                mock(ManagePortfolioPositionUseCase.class));
        ManagePortfolioPositionUseCase managePositionUseCase = mock(ManagePortfolioPositionUseCase.class);
        controller = new PortfolioController(mock(PortfolioOverviewService.class), mock(AddPortfolioPositionUseCase.class), managePositionUseCase);

        controller.updatePosition(
                positionId,
                new PortfolioController.UpdatePositionRequest(
                        new BigDecimal("3"),
                        new BigDecimal("160.00"),
                        LocalDate.of(2026, 4, 2),
                        new BigDecimal("2.00"),
                        "updated"),
                auth(userId, "FREE"));

        controller.closePosition(
                positionId,
                new PortfolioController.ClosePositionRequest(
                        new BigDecimal("175.00"),
                        Instant.parse("2026-04-20T10:00:00Z"),
                        new BigDecimal("1.50")),
                auth(userId, "FREE"));

        controller.deletePosition(positionId, auth(userId, "FREE"));

        verify(managePositionUseCase).update(new ManagePortfolioPositionUseCase.UpdateCommand(
                positionId,
                userId,
                new BigDecimal("3"),
                new BigDecimal("160.00"),
                new BigDecimal("2.00"),
                "updated",
                LocalDate.of(2026, 4, 2)));
        verify(managePositionUseCase).close(new ManagePortfolioPositionUseCase.CloseCommand(
                positionId,
                userId,
                new BigDecimal("175.00"),
                new BigDecimal("1.50"),
                Instant.parse("2026-04-20T10:00:00Z")));
        verify(managePositionUseCase).delete(positionId, userId);
    }

    private static Authentication auth(UUID userId, String plan) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new TokenClaims(userId, "user@example.com", plan));
        return authentication;
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
