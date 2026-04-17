package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BenchmarkComparison;
import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.util.List;

public interface BenchmarkComparator {
    BenchmarkComparison compare(List<EquityPoint> strategyCurve, List<EquityPoint> benchmarkCurve);
}
