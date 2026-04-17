package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestOrder;
import com.tradingsaas.tradingcore.domain.model.backtest.ExecutionResult;
import com.tradingsaas.tradingcore.domain.model.backtest.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
class DefaultSimulatedBroker implements SimulatedBroker {

    private static final BigDecimal DEFAULT_SLIPPAGE_PERCENT = new BigDecimal("0.001");
    private static final BigDecimal DEFAULT_COMMISSION_PER_SHARE = new BigDecimal("0.01");

    private final BigDecimal slippagePercent;
    private final BigDecimal commissionPerShare;
    private final Clock clock;

    DefaultSimulatedBroker() {
        this(DEFAULT_SLIPPAGE_PERCENT, DEFAULT_COMMISSION_PER_SHARE, Clock.systemUTC());
    }

    DefaultSimulatedBroker(BigDecimal slippagePercent, BigDecimal commissionPerShare, Clock clock) {
        this.slippagePercent = slippagePercent;
        this.commissionPerShare = commissionPerShare;
        this.clock = clock;
    }

    @Override
    public ExecutionResult execute(BacktestOrder order) {
        BigDecimal slippageApplied = order.requestedPrice().multiply(slippagePercent).setScale(6, RoundingMode.HALF_UP);
        BigDecimal fillPrice = switch (order.side()) {
            case BUY -> order.requestedPrice().add(slippageApplied);
            case SELL -> order.requestedPrice().subtract(slippageApplied);
        };
        fillPrice = fillPrice.setScale(6, RoundingMode.HALF_UP);

        BigDecimal commission = commissionPerShare
                .multiply(BigDecimal.valueOf(order.quantity()))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal grossValue = fillPrice.multiply(BigDecimal.valueOf(order.quantity())).setScale(6, RoundingMode.HALF_UP);
        BigDecimal cashImpact = switch (order.side()) {
            case BUY -> grossValue.add(commission).negate();
            case SELL -> grossValue.subtract(commission);
        };
        cashImpact = cashImpact.setScale(6, RoundingMode.HALF_UP);

        return new ExecutionResult(
                order.orderId(),
                order.symbol(),
                order.side(),
                order.quantity(),
                order.requestedPrice().setScale(6, RoundingMode.HALF_UP),
                fillPrice,
                commission,
                cashImpact,
                slippageApplied,
                Instant.now(clock)
        );
    }
}
