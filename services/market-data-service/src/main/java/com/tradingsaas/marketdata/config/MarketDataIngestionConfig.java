package com.tradingsaas.marketdata.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketDataIngestionConfig {

    @Bean
    public Clock marketDataClock(MarketDataIngestionProperties properties) {
        ZoneId zone = properties.zone() == null ? ZoneId.of("America/New_York") : properties.zone();
        return Clock.system(zone);
    }
}
