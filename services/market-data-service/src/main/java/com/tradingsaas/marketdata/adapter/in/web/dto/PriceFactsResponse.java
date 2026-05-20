package com.tradingsaas.marketdata.adapter.in.web.dto;

import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceFactsResponse(
        String ticker,
        TimeFrame timeframe,
        LocalDate snapshotAt,
        int barsAvailable,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal pctChange1d,
        BigDecimal pctChange5d,
        BigDecimal pctChange30d,
        BigDecimal high52w,
        BigDecimal low52w,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal sma200,
        BigDecimal rsi14,
        BigDecimal macdHistogram,
        long volume,
        BigDecimal volumeAvg20d,
        BigDecimal support,
        BigDecimal resistance) {

    public static PriceFactsResponse from(PriceFacts facts) {
        return new PriceFactsResponse(
                facts.ticker(),
                facts.timeFrame(),
                facts.snapshotAt(),
                facts.barsAvailable(),
                facts.close(),
                facts.previousClose(),
                facts.pctChange1d(),
                facts.pctChange5d(),
                facts.pctChange30d(),
                facts.high52w(),
                facts.low52w(),
                facts.sma20(),
                facts.sma50(),
                facts.sma200(),
                facts.rsi14(),
                facts.macdHistogram(),
                facts.volume(),
                facts.volumeAvg20d(),
                facts.support(),
                facts.resistance());
    }
}
