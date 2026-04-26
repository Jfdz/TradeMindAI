package com.tradingsaas.tradingcore.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserNotificationPreferences(
        UUID userId,
        boolean signalDigest,
        boolean liveAlerts,
        boolean riskWarnings,
        boolean strategyChanges,
        boolean weeklyRecap,
        Instant createdAt,
        Instant updatedAt
) {}
