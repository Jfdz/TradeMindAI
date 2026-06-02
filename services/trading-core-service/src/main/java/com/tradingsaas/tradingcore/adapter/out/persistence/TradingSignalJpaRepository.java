package com.tradingsaas.tradingcore.adapter.out.persistence;

import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradingSignalJpaRepository extends JpaRepository<TradingSignalJpaEntity, UUID> {

    Page<TradingSignalJpaEntity> findAllByOrderByGeneratedAtDesc(Pageable pageable);

    Optional<TradingSignalJpaEntity> findTopByOrderByGeneratedAtDesc();

    @Modifying
    @Query("UPDATE TradingSignalJpaEntity e SET e.reasoning = :reasoning, "
            + "e.reasoningStatus = :status, e.reasoningGeneratedAt = :generatedAt "
            + "WHERE e.id = :id")
    void updateReasoning(@Param("id") UUID id,
                         @Param("reasoning") String reasoning,
                         @Param("status") ReasoningStatus status,
                         @Param("generatedAt") Instant generatedAt);

    @Query("SELECT e FROM TradingSignalJpaEntity e "
            + "WHERE e.reasoningStatus = :status "
            + "AND (e.reasoningGeneratedAt IS NULL OR e.reasoningGeneratedAt < :olderThan) "
            + "ORDER BY e.generatedAt DESC")
    List<TradingSignalJpaEntity> findByReasoningStatusAndOlderThan(
            @Param("status") ReasoningStatus status,
            @Param("olderThan") Instant olderThan,
            Pageable pageable);

    @Query("SELECT e FROM TradingSignalJpaEntity e "
            + "WHERE e.ticker = :ticker "
            + "AND e.signalType = :signalType "
            + "AND e.timeframe = :timeframe "
            + "AND ((:entryPrice IS NULL AND e.entryPrice IS NULL) OR e.entryPrice = :entryPrice) "
            + "AND e.generatedAt >= :sinceAtLeast "
            + "ORDER BY e.generatedAt DESC")
    List<TradingSignalJpaEntity> findRecentEquivalent(
            @Param("ticker") String ticker,
            @Param("signalType") SignalType signalType,
            @Param("timeframe") Timeframe timeframe,
            @Param("entryPrice") BigDecimal entryPrice,
            @Param("sinceAtLeast") Instant sinceAtLeast,
            Pageable pageable);

    // E1 admin reasoning-audit explorer.
    Page<TradingSignalJpaEntity> findByTickerIgnoreCaseOrderByGeneratedAtDesc(
            String ticker, Pageable pageable);

    // Daily performance review: tradeable signals generated within the review window.
    // HOLD is excluded (no target/stop thesis to resolve). Resolved rows are filtered
    // out in-memory by the runner using the performance table.
    @Query("SELECT e FROM TradingSignalJpaEntity e "
            + "WHERE e.generatedAt > :cutoff "
            + "AND e.signalType <> :excluded "
            + "AND e.ticker IS NOT NULL "
            + "ORDER BY e.generatedAt DESC")
    List<TradingSignalJpaEntity> findReviewCandidates(
            @Param("cutoff") Instant cutoff,
            @Param("excluded") SignalType excluded,
            Pageable pageable);

    @Query("SELECT DISTINCT e.ticker FROM TradingSignalJpaEntity e "
            + "WHERE e.ticker IS NOT NULL ORDER BY e.ticker ASC")
    List<String> findDistinctTickers();
}
