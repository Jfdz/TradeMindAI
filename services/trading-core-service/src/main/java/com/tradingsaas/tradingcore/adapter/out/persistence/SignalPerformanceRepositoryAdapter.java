package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SignalPerformanceJpaEntity;
import com.tradingsaas.tradingcore.domain.model.RecentTickerPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.port.out.SignalPerformanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SignalPerformanceRepositoryAdapter implements SignalPerformanceRepository {

    private final SignalPerformanceJpaRepository repository;

    SignalPerformanceRepositoryAdapter(SignalPerformanceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SignalPerformance upsert(SignalPerformance performance) {
        // JpaRepository.save() upserts on the PK (signal_id); merge keeps it idempotent.
        Instant now = Instant.now();
        SignalPerformanceJpaEntity entity = new SignalPerformanceJpaEntity(
                performance.signalId(),
                performance.ticker(),
                performance.generatedAt(),
                performance.entryPrice(),
                performance.price1d(),
                performance.price3d(),
                performance.price7d(),
                performance.price30d(),
                performance.maxProfit(),
                performance.maxDrawdown(),
                performance.outcome(),
                performance.resolvedAt(),
                performance.evaluatedAt() != null ? performance.evaluatedAt() : now,
                now);
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignalPerformance> findBySignalId(UUID signalId) {
        return repository.findById(signalId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SignalPerformance> findBySignalIds(Collection<UUID> signalIds) {
        if (signalIds == null || signalIds.isEmpty()) {
            return Map.of();
        }
        return repository.findBySignalIdIn(List.copyOf(signalIds)).stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(SignalPerformance::signalId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignalPerformanceStat> aggregateStats() {
        return repository.aggregateStats().stream()
                .map(row -> {
                    long sample = row.getSampleSize();
                    BigDecimal winRate = sample == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(row.getWins())
                                    .divide(BigDecimal.valueOf(sample), 4, RoundingMode.HALF_EVEN);
                    return new SignalPerformanceStat(
                            SignalType.valueOf(row.getSignalType()),
                            row.getConfidenceBand(),
                            sample,
                            row.getWins(),
                            winRate,
                            row.getAvgMaxProfit(),
                            row.getAvgMaxDrawdown());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecentTickerPerformance recentPerformanceForTicker(String ticker, int limit) {
        SignalPerformanceJpaRepository.RecentPerfRow row =
                repository.recentPerformanceForTicker(ticker, Math.max(1, limit));
        if (row == null) {
            return new RecentTickerPerformance(0, 0, 0);
        }
        int wins = (int) row.getWins();
        int losses = (int) row.getLosses();
        return new RecentTickerPerformance(wins, losses, wins + losses);
    }

    private SignalPerformance toDomain(SignalPerformanceJpaEntity e) {
        return new SignalPerformance(
                e.getSignalId(),
                e.getTicker(),
                e.getGeneratedAt(),
                e.getEntryPrice(),
                e.getPrice1d(),
                e.getPrice3d(),
                e.getPrice7d(),
                e.getPrice30d(),
                e.getMaxProfit(),
                e.getMaxDrawdown(),
                e.getOutcome() != null ? e.getOutcome() : SignalOutcome.OPEN,
                e.getResolvedAt(),
                e.getEvaluatedAt());
    }
}
