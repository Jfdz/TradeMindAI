package com.tradingsaas.tradingcore.domain.model;

import java.util.UUID;

public record TokenClaims(UUID userId, String email, String subscriptionPlan) {}
