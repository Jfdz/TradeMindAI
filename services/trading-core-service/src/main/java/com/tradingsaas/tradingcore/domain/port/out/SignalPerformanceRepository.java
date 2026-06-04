package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.RecentTickerPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SignalPerformanceRepository {

    /** Insert or update (keyed by signalId) the performance snapshot. Idempotent. */
    SignalPerformance upsert(SignalPerformance performance);

    Optional<SignalPerformance> findBySignalId(UUID signalId);

    /** Batch lookup keyed by signalId; missing ids are simply absent from the map. */
    Map<UUID, SignalPerformance> findBySignalIds(Collection<UUID> signalIds);

    /**
     * Aggregate win-rate / avg-return / avg-drawdown over resolved (non-OPEN)
     * signals, sliced by signal type and a confidence band (>= 0.80 vs below).
     * Joins {@code trading_signals} in the same schema (no cross-schema read).
     */
    List<SignalPerformanceStat> aggregateStats();

    /**
     * Recent resolved (non-OPEN) win/loss counts for one ticker, over at most
     * {@code limit} most-recently-evaluated signals. Same-schema only. Used to
     * ground the reasoning context in the ticker's recent track record.
     */
    RecentTickerPerformance recentPerformanceForTicker(String ticker, int limit);
}
