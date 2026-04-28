package com.tradingsaas.marketdata.adapter.out.persistence;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.StockPriceEntity;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StockPriceJpaRepository extends JpaRepository<StockPriceEntity, Long> {

    @Query("""
            SELECT s FROM StockPriceEntity s JOIN FETCH s.symbol
            WHERE s.symbol.ticker = :ticker
            AND s.timeFrame = :timeFrame
            AND s.date BETWEEN :from AND :to
            ORDER BY s.date DESC
            """)
    Page<StockPriceEntity> findHistoricalByTickerAndTimeFrame(
            @Param("ticker") String ticker,
            @Param("timeFrame") TimeFrame timeFrame,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("""
            SELECT s FROM StockPriceEntity s JOIN FETCH s.symbol
            WHERE s.symbol.ticker = :ticker
            AND s.timeFrame = :timeFrame
            AND s.date BETWEEN :from AND :to
            ORDER BY s.date DESC
            """)
    List<StockPriceEntity> findAllByTickerAndTimeFrame(
            @Param("ticker") String ticker,
            @Param("timeFrame") TimeFrame timeFrame,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT s FROM StockPriceEntity s JOIN FETCH s.symbol
            WHERE s.symbol.ticker = :ticker AND s.timeFrame = :timeFrame
            ORDER BY s.date DESC
            """)
    Page<StockPriceEntity> findLatestPageByTickerAndTimeFrame(
            @Param("ticker") String ticker,
            @Param("timeFrame") TimeFrame timeFrame,
            Pageable pageable);

    default Optional<StockPriceEntity> findLatestByTickerAndTimeFrame(String ticker, TimeFrame timeFrame) {
        return findLatestPageByTickerAndTimeFrame(ticker, timeFrame,
                org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst();
    }

    @Query("""
            SELECT s FROM StockPriceEntity s JOIN FETCH s.symbol
            WHERE s.symbol.ticker IN :tickers
            AND s.timeFrame = :timeFrame
            AND s.date = (
                SELECT MAX(innerS.date)
                FROM StockPriceEntity innerS
                WHERE innerS.symbol.ticker = s.symbol.ticker
                AND innerS.timeFrame = s.timeFrame
            )
            """)
    List<StockPriceEntity> findLatestByTickersAndTimeFrame(
            @Param("tickers") List<String> tickers,
            @Param("timeFrame") TimeFrame timeFrame);
}
