package com.tradingsaas.tradingcore.adapter.in.web.annotation;

import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresSubscription {
    SubscriptionPlan value();
}
