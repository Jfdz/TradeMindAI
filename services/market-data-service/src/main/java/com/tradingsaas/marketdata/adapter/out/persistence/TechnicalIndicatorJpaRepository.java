package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.TechnicalIndicatorEntity;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TechnicalIndicatorJpaRepository extends JpaRepository<TechnicalIndicatorEntity, Long> {

    @Query("SELECT t FROM TechnicalIndicatorEntity t WHERE t.symbol.ticker = :ticker AND t.date = :date")
    List<TechnicalIndicatorEntity> findBySymbolTickerAndDate(
            @Param("ticker") String ticker,
            @Param("date") LocalDate date);

    @Query("SELECT t FROM TechnicalIndicatorEntity t WHERE t.symbol.ticker = :ticker AND t.date BETWEEN :from AND :to ORDER BY t.date ASC")
    List<TechnicalIndicatorEntity> findBySymbolTickerAndDateBetween(
            @Param("ticker") String ticker,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT t FROM TechnicalIndicatorEntity t WHERE t.symbol.ticker = :ticker AND t.date = :date AND t.type = :type")
    Optional<TechnicalIndicatorEntity> findBySymbolTickerAndDateAndType(
            @Param("ticker") String ticker,
            @Param("date") LocalDate date,
            @Param("type") TechnicalIndicatorType type);

    @Query("""
            SELECT t FROM TechnicalIndicatorEntity t
            WHERE t.symbol.ticker = :ticker
            AND t.type IN :types
            AND t.date = (
                SELECT MAX(t2.date) FROM TechnicalIndicatorEntity t2
                WHERE t2.symbol.ticker = :ticker AND t2.type = t.type
            )
            ORDER BY t.type ASC
            """)
    List<TechnicalIndicatorEntity> findLatestByTickerAndTypes(
            @Param("ticker") String ticker,
            @Param("types") Collection<TechnicalIndicatorType> types);

    @Query("""
            SELECT t FROM TechnicalIndicatorEntity t
            WHERE t.symbol.ticker = :ticker
            AND t.date = (
                SELECT MAX(t2.date) FROM TechnicalIndicatorEntity t2
                WHERE t2.symbol.ticker = :ticker AND t2.type = t.type
            )
            ORDER BY t.type ASC
            """)
    List<TechnicalIndicatorEntity> findLatestByTicker(@Param("ticker") String ticker);
}
