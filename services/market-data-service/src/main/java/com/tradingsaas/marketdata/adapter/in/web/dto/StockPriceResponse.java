package com.tradingsaas.marketdata.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;

public record StockPriceResponse(
        String ticker,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        TimeFrame timeFrame,
        OhlcvResponse ohlcv,
        BigDecimal adjustedClose) {
}
