package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase.StrategyCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StrategyRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull @Positive @DecimalMax(value = "50.0", inclusive = true) BigDecimal stopLossPct,
        @NotNull @Positive @DecimalMax(value = "100.0", inclusive = true) BigDecimal takeProfitPct,
        @NotNull @Positive @DecimalMax(value = "100.0", inclusive = true) BigDecimal maxPositionPct,
        Boolean active) {

    public StrategyCommand toCommand() {
        return new StrategyCommand(name, description, stopLossPct, takeProfitPct, maxPositionPct, active == null || active);
    }
}
