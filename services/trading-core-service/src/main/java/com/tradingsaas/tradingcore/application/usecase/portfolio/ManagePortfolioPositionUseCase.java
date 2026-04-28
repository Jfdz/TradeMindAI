package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface ManagePortfolioPositionUseCase {

    record UpdateCommand(UUID positionId, UUID userId, BigDecimal quantity,
                         BigDecimal entryPrice, BigDecimal fees, String notes, LocalDate purchaseDate) {}

    record CloseCommand(UUID positionId, UUID userId, BigDecimal exitPrice, BigDecimal fees, Instant closedAt) {}

    void update(UpdateCommand command);

    void close(CloseCommand command);

    void delete(UUID positionId, UUID userId);
}
