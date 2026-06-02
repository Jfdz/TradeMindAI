package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalPerformanceUseCase;
import com.tradingsaas.tradingcore.domain.port.out.SignalPerformanceRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SignalPerformanceQueryService implements GetSignalPerformanceUseCase {

    private final SignalPerformanceRepository performanceRepository;

    SignalPerformanceQueryService(SignalPerformanceRepository performanceRepository) {
        this.performanceRepository = performanceRepository;
    }

    @Override
    public Optional<SignalPerformance> findOne(UUID signalId) {
        return performanceRepository.findBySignalId(signalId);
    }

    @Override
    public Map<UUID, SignalPerformance> findFor(Collection<UUID> signalIds) {
        return performanceRepository.findBySignalIds(signalIds);
    }

    @Override
    public List<SignalPerformanceStat> stats() {
        return performanceRepository.aggregateStats();
    }
}
