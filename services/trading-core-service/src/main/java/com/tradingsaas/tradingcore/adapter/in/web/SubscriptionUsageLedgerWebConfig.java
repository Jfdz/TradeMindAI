package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.SubscriptionUsageLedgerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SubscriptionUsageLedgerWebConfig implements WebMvcConfigurer {

    private final SubscriptionUsageLedgerInterceptor subscriptionUsageLedgerInterceptor;

    public SubscriptionUsageLedgerWebConfig(SubscriptionUsageLedgerService ledgerService) {
        this.subscriptionUsageLedgerInterceptor = new SubscriptionUsageLedgerInterceptor(ledgerService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(subscriptionUsageLedgerInterceptor);
    }

    @Bean
    SubscriptionUsageLedgerInterceptor subscriptionUsageLedgerInterceptor() {
        return subscriptionUsageLedgerInterceptor;
    }
}
