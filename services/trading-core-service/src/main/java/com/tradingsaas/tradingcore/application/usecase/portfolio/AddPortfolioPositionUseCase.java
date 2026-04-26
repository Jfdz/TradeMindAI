package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface AddPortfolioPositionUseCase {

    record AddPositionCommand(
            UUID userId,
            String subscriptionPlan,
            String ticker,
            BigDecimal quantity,
            BigDecimal entryPrice,
            LocalDate purchaseDate,
            BigDecimal fees,
            String notes) {}

    UUID addPosition(AddPositionCommand command);
}
