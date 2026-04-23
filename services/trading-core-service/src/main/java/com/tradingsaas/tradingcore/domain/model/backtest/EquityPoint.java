package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(Instant timestamp, BigDecimal equity) {
}
