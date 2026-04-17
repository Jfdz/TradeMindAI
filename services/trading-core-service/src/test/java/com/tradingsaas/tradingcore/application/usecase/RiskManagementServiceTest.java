package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskManagementServiceTest {

    private final RiskManagementService service = new RiskManagementService();

    @Test
    void calculatesBuySideStopLossAndTakeProfitPrices() {
        BigDecimal stopLoss = service.calculateStopLossPrice(new BigDecimal("178.50"), new BigDecimal("2.00"), SignalType.BUY);
        BigDecimal takeProfit = service.calculateTakeProfitPrice(new BigDecimal("178.50"), new BigDecimal("4.00"), SignalType.BUY);

        assertEquals(0, stopLoss.compareTo(new BigDecimal("174.93")));
        assertEquals(0, takeProfit.compareTo(new BigDecimal("185.64")));
    }

    @Test
    void calculatesSellSideRiskPrices() {
        BigDecimal stopLoss = service.calculateStopLossPrice(new BigDecimal("100.00"), new BigDecimal("2.00"), SignalType.SELL);
        BigDecimal takeProfit = service.calculateTakeProfitPrice(new BigDecimal("100.00"), new BigDecimal("4.00"), SignalType.SELL);

        assertEquals(0, stopLoss.compareTo(new BigDecimal("102.00")));
        assertEquals(0, takeProfit.compareTo(new BigDecimal("96.00")));
    }

    @Test
    void calculatesKellySizingAndCapsToMaximumPosition() {
        BigDecimal kellyFraction = service.calculateKellyFraction(new BigDecimal("0.60"), new BigDecimal("1.50"));
        BigDecimal positionValue = service.calculateKellyPositionValue(
                new BigDecimal("10000.00"),
                new BigDecimal("0.60"),
                new BigDecimal("1.50"),
                new BigDecimal("10.00"));

        assertEquals(0, kellyFraction.compareTo(new BigDecimal("0.3333")));
        assertEquals(0, positionValue.compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void calculatesFixedPositionSizeFromEquityAndRiskLimit() {
        BigDecimal size = service.calculateFixedPositionSize(
                new BigDecimal("10000.00"),
                new BigDecimal("178.50"),
                new RiskParameters(new BigDecimal("2.00"), new BigDecimal("4.00"), new BigDecimal("10.00")));

        assertEquals(0, size.compareTo(new BigDecimal("5.60224090")));
    }

    @Test
    void rejectsHoldSignalForPriceCalculations() {
        assertThrows(IllegalArgumentException.class, () ->
                service.calculateStopLossPrice(new BigDecimal("100.00"), new BigDecimal("2.00"), SignalType.HOLD));
    }
}
