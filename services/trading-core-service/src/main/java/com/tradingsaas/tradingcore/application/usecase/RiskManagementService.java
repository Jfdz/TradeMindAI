package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class RiskManagementService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public BigDecimal calculateStopLossPrice(BigDecimal entryPrice, BigDecimal stopLossPct, SignalType signalType) {
        validatePriceInputs(entryPrice, stopLossPct, signalType);
        BigDecimal offset = entryPrice.multiply(stopLossPct).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal result = signalType == SignalType.BUY
                ? entryPrice.subtract(offset)
                : entryPrice.add(offset);
        return scalePrice(result);
    }

    public BigDecimal calculateTakeProfitPrice(BigDecimal entryPrice, BigDecimal takeProfitPct, SignalType signalType) {
        validatePriceInputs(entryPrice, takeProfitPct, signalType);
        BigDecimal offset = entryPrice.multiply(takeProfitPct).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal result = signalType == SignalType.BUY
                ? entryPrice.add(offset)
                : entryPrice.subtract(offset);
        return scalePrice(result);
    }

    public BigDecimal calculateFixedPositionValue(BigDecimal accountEquity, BigDecimal maxPositionPct) {
        validatePositive(accountEquity, "accountEquity");
        validatePositive(maxPositionPct, "maxPositionPct");
        return scaleMoney(accountEquity.multiply(maxPositionPct).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    public BigDecimal calculateKellyFraction(BigDecimal winRate, BigDecimal rewardRiskRatio) {
        validatePositive(winRate, "winRate");
        validatePositive(rewardRiskRatio, "rewardRiskRatio");
        if (winRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("winRate must not be greater than 1");
        }

        BigDecimal lossRate = BigDecimal.ONE.subtract(winRate);
        BigDecimal rawFraction = winRate.subtract(lossRate.divide(rewardRiskRatio, 8, RoundingMode.HALF_UP));
        if (rawFraction.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (rawFraction.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return rawFraction.setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateKellyPositionValue(BigDecimal accountEquity,
                                           BigDecimal winRate,
                                           BigDecimal rewardRiskRatio,
                                           BigDecimal maxPositionPct) {
        BigDecimal kellyFraction = calculateKellyFraction(winRate, rewardRiskRatio);
        BigDecimal cappedFraction = kellyFraction.min(maxPositionPct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return scaleMoney(accountEquity.multiply(cappedFraction));
    }

    public BigDecimal calculateFixedPositionSize(BigDecimal accountEquity, BigDecimal entryPrice, RiskParameters riskParameters) {
        BigDecimal positionValue = calculateFixedPositionValue(accountEquity, riskParameters.getMaxPositionPct());
        validatePriceInputs(entryPrice, BigDecimal.ONE, SignalType.BUY);
        return positionValue.divide(entryPrice, 8, RoundingMode.HALF_UP);
    }

    private void validatePriceInputs(BigDecimal entryPrice, BigDecimal pct, SignalType signalType) {
        validatePositive(entryPrice, "entryPrice");
        validatePositive(pct, "percentage");
        if (signalType == null || signalType == SignalType.HOLD) {
            throw new IllegalArgumentException("signalType must be BUY or SELL");
        }
    }

    private void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
