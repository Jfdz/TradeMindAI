package com.tradingsaas.tradingcore.domain.exception;

import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;

public class InsufficientSubscriptionException extends RuntimeException {

    private final SubscriptionPlan required;

    public InsufficientSubscriptionException(SubscriptionPlan required) {
        super("Upgrade required: " + required.name() + " subscription or higher needed");
        this.required = required;
    }

    public SubscriptionPlan getRequired() {
        return required;
    }
}
