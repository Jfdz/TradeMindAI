package com.tradingsaas.tradingcore.application.usecase.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioPositionJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ManagePortfolioPositionUseCaseImplTest {

    @Test
    void closesOpenPositionWithOptionalFeesAndDefaultTimestamp() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID positionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioPositionJpaRepository repository = mock(PortfolioPositionJpaRepository.class);
        ManagePortfolioPositionUseCaseImpl useCase = new ManagePortfolioPositionUseCaseImpl(repository);
        PortfolioPositionJpaEntity position = position(positionId, userId, "OPEN");

        when(repository.findByIdAndUserId(positionId, userId)).thenReturn(Optional.of(position));

        useCase.close(new ManagePortfolioPositionUseCase.CloseCommand(
                positionId, userId, new BigDecimal("175.00"), new BigDecimal("1.50"), null));

        assertEquals("CLOSED", position.getStatus());
        assertEquals(0, position.getExitPrice().compareTo(new BigDecimal("175.00")));
        assertEquals(0, position.getFees().compareTo(new BigDecimal("3.50")));
        assertNotNull(position.getClosedAt());
        verify(repository).save(position);
    }

    @Test
    void rejectsClosingAnAlreadyClosedPosition() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID positionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioPositionJpaRepository repository = mock(PortfolioPositionJpaRepository.class);
        ManagePortfolioPositionUseCaseImpl useCase = new ManagePortfolioPositionUseCaseImpl(repository);
        PortfolioPositionJpaEntity position = position(positionId, userId, "CLOSED");
        position.close(new BigDecimal("170.00"), BigDecimal.ZERO, Instant.parse("2026-04-20T10:00:00Z"));

        when(repository.findByIdAndUserId(positionId, userId)).thenReturn(Optional.of(position));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> useCase.close(
                new ManagePortfolioPositionUseCase.CloseCommand(
                        positionId, userId, new BigDecimal("175.00"), BigDecimal.ZERO, Instant.now())));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private static PortfolioPositionJpaEntity position(UUID positionId, UUID userId, String status) {
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
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                user,
                new BigDecimal("1000.00"),
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"));
        return new PortfolioPositionJpaEntity(
                positionId,
                portfolio,
                "AAPL",
                new BigDecimal("2"),
                new BigDecimal("150.00"),
                new BigDecimal("2.00"),
                "notes",
                LocalDate.of(2026, 4, 1),
                status,
                Instant.parse("2026-04-16T10:00:00Z"));
    }
}
