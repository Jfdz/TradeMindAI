package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradingSignalRepository {

    TradingSignal save(TradingSignal signal);

    Page<TradingSignal> findAll(Pageable pageable);

    Optional<TradingSignal> findById(UUID id);

    Optional<TradingSignal> findLatest();
}
