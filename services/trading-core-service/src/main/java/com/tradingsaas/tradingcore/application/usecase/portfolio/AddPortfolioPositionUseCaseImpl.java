package com.tradingsaas.tradingcore.application.usecase.portfolio;

import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.PortfolioPositionJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.UserJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.PortfolioPositionJpaEntity;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.UserJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddPortfolioPositionUseCaseImpl implements AddPortfolioPositionUseCase {

    private final PortfolioJpaRepository portfolioRepository;
    private final PortfolioPositionJpaRepository positionRepository;
    private final UserJpaRepository userRepository;

    public AddPortfolioPositionUseCaseImpl(
            PortfolioJpaRepository portfolioRepository,
            PortfolioPositionJpaRepository positionRepository,
            UserJpaRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UUID addPosition(AddPositionCommand command) {
        PortfolioJpaEntity portfolio = portfolioRepository.findByUser_Id(command.userId())
                .orElseGet(() -> createPortfolio(command.userId(), command.subscriptionPlan()));

        PortfolioPositionJpaEntity position = new PortfolioPositionJpaEntity(
                UUID.randomUUID(),
                portfolio,
                command.ticker().trim().toUpperCase(),
                command.quantity(),
                command.entryPrice(),
                command.fees(),
                command.notes(),
                command.purchaseDate(),
                "OPEN",
                Instant.now()
        );

        return positionRepository.save(position).getId();
    }

    private PortfolioJpaEntity createPortfolio(UUID userId, String plan) {
        UserJpaEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Instant now = Instant.now();
        PortfolioJpaEntity portfolio = new PortfolioJpaEntity(
                UUID.randomUUID(),
                user,
                defaultCapital(plan),
                now,
                now
        );
        return portfolioRepository.save(portfolio);
    }

    private static BigDecimal defaultCapital(String plan) {
        return switch (plan == null ? "FREE" : plan.toUpperCase()) {
            case "BASIC" -> BigDecimal.valueOf(25_000);
            case "PREMIUM" -> BigDecimal.valueOf(100_000);
            default -> BigDecimal.valueOf(10_000);
        };
    }
}
