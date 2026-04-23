package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import com.tradingsaas.tradingcore.domain.model.backtest.ExecutionResult;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.model.backtest.OrderSide;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioPosition;
import com.tradingsaas.tradingcore.domain.model.backtest.PortfolioSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
class DefaultPortfolioTracker implements PortfolioTracker {

    private static final int SCALE = 6;

    private final Map<String, MutablePosition> positions = new LinkedHashMap<>();
    private final List<EquityPoint> equityCurve = new ArrayList<>();
    private BigDecimal cash = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    private BigDecimal realizedPnl = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    @Override
    public void applyFill(ExecutionResult executionResult) {
        cash = cash.add(executionResult.cashImpact()).setScale(SCALE, RoundingMode.HALF_UP);

        MutablePosition position = positions.computeIfAbsent(
                executionResult.symbol(),
                key -> new MutablePosition(key, 0, BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
        );

        BigDecimal fillPrice = executionResult.fillPrice();
        int quantity = executionResult.quantity();

        if (executionResult.side() == OrderSide.BUY) {
            BigDecimal currentCost = position.averageCost.multiply(BigDecimal.valueOf(position.quantity));
            BigDecimal fillCost = fillPrice.multiply(BigDecimal.valueOf(quantity));
            int newQuantity = position.quantity + quantity;
            BigDecimal updatedAverageCost = currentCost.add(fillCost)
                    .divide(BigDecimal.valueOf(newQuantity), SCALE, RoundingMode.HALF_UP);
            position.quantity = newQuantity;
            position.averageCost = updatedAverageCost;
        } else {
            int sellQuantity = Math.min(quantity, position.quantity);
            BigDecimal pnl = fillPrice.subtract(position.averageCost)
                    .multiply(BigDecimal.valueOf(sellQuantity))
                    .subtract(executionResult.commission())
                    .setScale(SCALE, RoundingMode.HALF_UP);
            realizedPnl = realizedPnl.add(pnl).setScale(SCALE, RoundingMode.HALF_UP);
            position.quantity -= sellQuantity;
            if (position.quantity == 0) {
                position.averageCost = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
            }
        }

        position.lastPrice = fillPrice;

        if (position.quantity == 0 && executionResult.side() == OrderSide.SELL) {
            positions.remove(executionResult.symbol());
        }
    }

    @Override
    public PortfolioSnapshot markToMarket(String symbol, OhlcvBar bar) {
        MutablePosition position = positions.get(symbol);
        if (position != null) {
            position.lastPrice = BigDecimal.valueOf(bar.close()).setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal unrealizedPnl = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal equity = cash;
        Map<String, PortfolioPosition> immutablePositions = new LinkedHashMap<>();

        for (MutablePosition current : positions.values()) {
            BigDecimal marketPrice = current.lastPrice;
            BigDecimal positionMarketValue = marketPrice.multiply(BigDecimal.valueOf(current.quantity)).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal positionCostBasis = current.averageCost.multiply(BigDecimal.valueOf(current.quantity)).setScale(SCALE, RoundingMode.HALF_UP);

            unrealizedPnl = unrealizedPnl.add(positionMarketValue.subtract(positionCostBasis)).setScale(SCALE, RoundingMode.HALF_UP);
            equity = equity.add(positionMarketValue).setScale(SCALE, RoundingMode.HALF_UP);
            immutablePositions.put(
                    current.symbol,
                    new PortfolioPosition(current.symbol, current.quantity, current.averageCost, current.lastPrice)
            );
        }

        EquityPoint point = new EquityPoint(bar.timestamp(), equity);
        equityCurve.add(point);

        return new PortfolioSnapshot(cash, realizedPnl, unrealizedPnl, equity, Map.copyOf(immutablePositions));
    }

    @Override
    public PortfolioSnapshot snapshot() {
        BigDecimal unrealizedPnl = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal equity = cash;
        Map<String, PortfolioPosition> immutablePositions = new LinkedHashMap<>();

        for (MutablePosition current : positions.values()) {
            BigDecimal positionMarketValue = current.lastPrice.multiply(BigDecimal.valueOf(current.quantity)).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal positionCostBasis = current.averageCost.multiply(BigDecimal.valueOf(current.quantity)).setScale(SCALE, RoundingMode.HALF_UP);

            unrealizedPnl = unrealizedPnl.add(positionMarketValue.subtract(positionCostBasis)).setScale(SCALE, RoundingMode.HALF_UP);
            equity = equity.add(positionMarketValue).setScale(SCALE, RoundingMode.HALF_UP);
            immutablePositions.put(
                    current.symbol,
                    new PortfolioPosition(current.symbol, current.quantity, current.averageCost, current.lastPrice)
            );
        }

        return new PortfolioSnapshot(cash, realizedPnl, unrealizedPnl, equity, Map.copyOf(immutablePositions));
    }

    @Override
    public List<EquityPoint> equityCurve() {
        return List.copyOf(equityCurve);
    }

    private static final class MutablePosition {
        private final String symbol;
        private int quantity;
        private BigDecimal averageCost;
        private BigDecimal lastPrice;

        private MutablePosition(String symbol, int quantity, BigDecimal averageCost, BigDecimal lastPrice) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.averageCost = averageCost;
            this.lastPrice = lastPrice;
        }
    }
}
