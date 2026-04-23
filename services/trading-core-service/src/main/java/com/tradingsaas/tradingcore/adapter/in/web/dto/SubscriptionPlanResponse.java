package com.tradingsaas.tradingcore.adapter.in.web.dto;

import java.util.List;

public record SubscriptionPlanResponse(
        String plan,
        String displayName,
        int maxSignalsPerDay,
        int maxStrategies,
        List<String> features
) {}
