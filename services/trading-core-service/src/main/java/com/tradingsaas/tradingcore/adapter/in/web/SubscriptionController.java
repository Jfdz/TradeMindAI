package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.SubscriptionPlanResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
class SubscriptionController {

    private static final List<SubscriptionPlanResponse> PLANS = List.of(
        new SubscriptionPlanResponse(
            "FREE", "Free Tier", 5, 1,
            List.of("5 trading signals per day", "1 active strategy", "Basic market data")
        ),
        new SubscriptionPlanResponse(
            "BASIC", "Basic", 50, 5,
            List.of("50 trading signals per day", "5 active strategies", "Real-time market data",
                    "Email notifications")
        ),
        new SubscriptionPlanResponse(
            "PREMIUM", "Premium", Integer.MAX_VALUE, Integer.MAX_VALUE,
            List.of("Unlimited trading signals", "Unlimited strategies", "Real-time market data",
                    "Priority support", "Advanced AI predictions", "Backtesting engine")
        )
    );

    @GetMapping("/plans")
    List<SubscriptionPlanResponse> getPlans() {
        return PLANS;
    }
}
