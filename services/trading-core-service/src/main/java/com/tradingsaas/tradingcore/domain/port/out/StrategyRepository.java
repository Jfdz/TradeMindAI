package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.Strategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StrategyRepository {

    Strategy save(Strategy strategy);

    Page<Strategy> findAllByUserId(UUID userId, Pageable pageable);

    Optional<Strategy> findByIdAndUserId(UUID id, UUID userId);

    void delete(Strategy strategy);
}
