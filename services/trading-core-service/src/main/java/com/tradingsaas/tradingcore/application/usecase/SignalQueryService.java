package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalsUseCase;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class SignalQueryService implements GetSignalsUseCase {

    private final TradingSignalRepository tradingSignalRepository;

    SignalQueryService(TradingSignalRepository tradingSignalRepository) {
        this.tradingSignalRepository = tradingSignalRepository;
    }

    @Override
    public Page<TradingSignal> getSignals(Pageable pageable) {
        return tradingSignalRepository.findAll(pageable);
    }

    @Override
    public Optional<TradingSignal> getLatest() {
        return tradingSignalRepository.findLatest();
    }

    @Override
    public Optional<TradingSignal> getById(UUID id) {
        return tradingSignalRepository.findById(id);
    }
}
