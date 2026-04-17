package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetSignalsUseCase {

    Page<TradingSignal> getSignals(Pageable pageable);

    Optional<TradingSignal> getLatest();

    Optional<TradingSignal> getById(UUID id);
}
