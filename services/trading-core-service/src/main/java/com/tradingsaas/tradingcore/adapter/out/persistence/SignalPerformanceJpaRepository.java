package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SignalPerformanceJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalPerformanceJpaRepository extends JpaRepository<SignalPerformanceJpaEntity, UUID> {

    List<SignalPerformanceJpaEntity> findBySignalIdIn(List<UUID> signalIds);

    /**
     * Aggregate resolved (non-OPEN) performance by signal type and confidence band.
     * Native join with trading_signals (same schema). The CASE on confidence
     * produces the band; grouping on it keeps HIGH (&ge; 0.80) and STANDARD apart.
     */
    @Query(value = """
            WITH resolved AS (
                SELECT s.signal_type AS signal_type,
                       CASE WHEN s.confidence >= 0.80 THEN 'HIGH' ELSE 'STANDARD' END AS confidence_band,
                       p.max_profit AS max_profit,
                       p.max_drawdown AS max_drawdown,
                       CASE WHEN p.outcome = 'WIN' THEN 1 ELSE 0 END AS is_win
                FROM trading_core.signal_performance p
                JOIN trading_core.trading_signals s ON s.id = p.signal_id
                WHERE p.outcome <> 'OPEN'
            )
            SELECT signal_type AS "signalType",
                   confidence_band AS "confidenceBand",
                   COUNT(*) AS "sampleSize",
                   SUM(is_win) AS "wins",
                   AVG(max_profit) AS "avgMaxProfit",
                   AVG(max_drawdown) AS "avgMaxDrawdown"
            FROM resolved
            GROUP BY signal_type, confidence_band
            ORDER BY signal_type, confidence_band
            """, nativeQuery = true)
    List<StatRow> aggregateStats();

    /** Native projection for {@link #aggregateStats()}. */
    interface StatRow {
        String getSignalType();
        String getConfidenceBand();
        long getSampleSize();
        long getWins();
        BigDecimal getAvgMaxProfit();
        BigDecimal getAvgMaxDrawdown();
    }

    /**
     * Recent resolved (non-OPEN) win/loss counts for one ticker over the most
     * recently evaluated signals. The inner LIMIT bounds the window; the outer
     * aggregate counts outcomes. COALESCE keeps the counts 0 (never null) when
     * the ticker has no resolved history. Same schema only — no cross-schema read.
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN recent.outcome = 'WIN' THEN 1 ELSE 0 END), 0) AS "wins",
                COALESCE(SUM(CASE WHEN recent.outcome = 'LOSS' THEN 1 ELSE 0 END), 0) AS "losses"
            FROM (
                SELECT p.outcome AS outcome
                FROM trading_core.signal_performance p
                WHERE p.ticker = :ticker AND p.outcome <> 'OPEN'
                ORDER BY p.evaluated_at DESC
                LIMIT :limit
            ) recent
            """, nativeQuery = true)
    RecentPerfRow recentPerformanceForTicker(@Param("ticker") String ticker, @Param("limit") int limit);

    /** Native projection for {@link #recentPerformanceForTicker(String, int)}. */
    interface RecentPerfRow {
        long getWins();
        long getLosses();
    }
}
