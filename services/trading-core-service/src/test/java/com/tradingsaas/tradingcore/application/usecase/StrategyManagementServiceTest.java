package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.domain.exception.StrategyNotFoundException;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase.StrategyCommand;
import com.tradingsaas.tradingcore.domain.port.out.StrategyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class StrategyManagementServiceTest {

    @Test
    void createsStrategyForUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        InMemoryStrategyRepository repository = new InMemoryStrategyRepository();
        StrategyManagementService service = new StrategyManagementService(repository);

        Strategy created = service.createStrategy(userId, new StrategyCommand(
                "Trend Following",
                "desc",
                new BigDecimal("2.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                true));

        assertEquals(userId, created.getUserId());
        assertEquals("Trend Following", created.getName());
        assertEquals(1, repository.store.size());
    }

    @Test
    void rejectsUpdateForOtherUser() {
        UUID ownerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID otherUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID strategyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        InMemoryStrategyRepository repository = new InMemoryStrategyRepository();
        repository.store.put(strategyId, strategy(ownerId, strategyId));
        StrategyManagementService service = new StrategyManagementService(repository);

        assertThrows(StrategyNotFoundException.class, () -> service.updateStrategy(otherUserId, strategyId, new StrategyCommand(
                "Trend Following",
                "desc",
                new BigDecimal("2.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                true)));
    }

    private static Strategy strategy(UUID userId, UUID strategyId) {
        Instant now = Instant.parse("2026-04-17T10:00:00Z");
        return new Strategy(
                strategyId,
                userId,
                "Trend Following",
                "desc",
                new com.tradingsaas.tradingcore.domain.model.RiskParameters(
                        new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00")),
                true,
                now,
                now);
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<UUID, Strategy> store = new HashMap<>();

        @Override
        public Strategy save(Strategy strategy) {
            store.put(strategy.getId(), strategy);
            return strategy;
        }

        @Override
        public Page<Strategy> findAllByUserId(UUID userId, org.springframework.data.domain.Pageable pageable) {
            return new PageImpl<>(store.values().stream().filter(s -> s.getUserId().equals(userId)).toList(), pageable, store.size());
        }

        @Override
        public Optional<Strategy> findByIdAndUserId(UUID id, UUID userId) {
            return Optional.ofNullable(store.get(id)).filter(s -> s.getUserId().equals(userId));
        }

        @Override
        public void delete(Strategy strategy) {
            store.remove(strategy.getId());
        }
    }
}
