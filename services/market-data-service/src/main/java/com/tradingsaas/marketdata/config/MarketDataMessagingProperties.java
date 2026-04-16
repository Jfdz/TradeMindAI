package com.tradingsaas.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data.events")
public record MarketDataMessagingProperties(String pricesUpdatedRoutingKey) {}
