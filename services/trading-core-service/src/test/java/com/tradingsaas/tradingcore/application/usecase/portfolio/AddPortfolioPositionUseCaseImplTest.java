package com.tradingsaas.tradingcore.application.usecase.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioPositionJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.UserJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AddPortfolioPositionUseCaseImplTest {

    @Test
    void addsPositionToExistingPortfolioWithoutCreatingAnotherOne() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        PortfolioPositionJpaRepository positionRepository = Mockito.mock(PortfolioPositionJpaRepository.class);
        UserJpaRepository userRepository = Mockito.mock(UserJpaRepository.class);
        AddPortfolioPositionUseCaseImpl useCase = new AddPortfolioPositionUseCaseImpl(
                portfolioRepository, positionRepository, userRepository);

        PortfolioJpaEntity portfolio = portfolio(userId, BigDecimal.valueOf(10_000));
        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.of(portfolio));
        when(positionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID positionId = useCase.addPosition(new AddPortfolioPositionUseCase.AddPositionCommand(
                userId,
                "FREE",
                " aapl ",
                new BigDecimal("2.5"),
                new BigDecimal("123.45"),
                LocalDate.of(2026, 4, 1),
                new BigDecimal("1.25"),
                "swing trade"));

        ArgumentCaptor<PortfolioPositionJpaEntity> captor = ArgumentCaptor.forClass(PortfolioPositionJpaEntity.class);
        verify(positionRepository).save(captor.capture());
        verify(portfolioRepository, never()).save(any());
        verify(userRepository, never()).findById(any());

        PortfolioPositionJpaEntity saved = captor.getValue();
        assertEquals(positionId, saved.getId());
        assertEquals(portfolio, saved.getPortfolio());
        assertEquals("AAPL", saved.getSymbolTicker());
        assertEquals(0, saved.getQuantity().compareTo(new BigDecimal("2.5")));
        assertEquals(0, saved.getEntryPrice().compareTo(new BigDecimal("123.45")));
        assertEquals(0, saved.getFees().compareTo(new BigDecimal("1.25")));
        assertEquals("swing trade", saved.getNotes());
        assertEquals("OPEN", saved.getStatus());
        assertNotNull(saved.getOpenedAt());
    }

    @Test
    void createsPortfolioUsingPlanCapitalWhenMissing() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioJpaRepository portfolioRepository = Mockito.mock(PortfolioJpaRepository.class);
        PortfolioPositionJpaRepository positionRepository = Mockito.mock(PortfolioPositionJpaRepository.class);
        UserJpaRepository userRepository = Mockito.mock(UserJpaRepository.class);
        AddPortfolioPositionUseCaseImpl useCase = new AddPortfolioPositionUseCaseImpl(
                portfolioRepository, positionRepository, userRepository);

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
        PortfolioJpaEntity savedPortfolio = portfolio(userId, BigDecimal.valueOf(25_000));

        when(portfolioRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(portfolioRepository.save(any())).thenReturn(savedPortfolio);
        when(positionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.addPosition(new AddPortfolioPositionUseCase.AddPositionCommand(
                userId,
                "basic",
                "msft",
                new BigDecimal("1"),
                new BigDecimal("250.00"),
                LocalDate.of(2026, 4, 2),
                BigDecimal.ZERO,
                null));

        ArgumentCaptor<PortfolioJpaEntity> portfolioCaptor = ArgumentCaptor.forClass(PortfolioJpaEntity.class);
        ArgumentCaptor<PortfolioPositionJpaEntity> positionCaptor = ArgumentCaptor.forClass(PortfolioPositionJpaEntity.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        verify(positionRepository).save(positionCaptor.capture());

        assertEquals(0, portfolioCaptor.getValue().getInitialCapital().compareTo(new BigDecimal("25000")));
        assertEquals(user, portfolioCaptor.getValue().getUser());
        assertEquals("MSFT", positionCaptor.getValue().getSymbolTicker());
        assertEquals(savedPortfolio, positionCaptor.getValue().getPortfolio());
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
}
