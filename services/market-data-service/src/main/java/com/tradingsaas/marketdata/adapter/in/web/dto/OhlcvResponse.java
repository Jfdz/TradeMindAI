package com.tradingsaas.marketdata.adapter.in.web.dto;

import java.math.BigDecimal;

public record OhlcvResponse(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
