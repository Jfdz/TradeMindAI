package com.tradingsaas.tradingcore.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String subscriptionPlan,
        Instant createdAt
) {}
