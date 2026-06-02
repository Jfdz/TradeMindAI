package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface GetSignalPerformanceUseCase {

    Optional<SignalPerformance> findOne(UUID signalId);

    /** Batch lookup so the signals list endpoint avoids N+1. */
    Map<UUID, SignalPerformance> findFor(Collection<UUID> signalIds);

    /** Aggregate win-rate / avg-return / avg-drawdown by type and confidence band. */
    List<SignalPerformanceStat> stats();
}
