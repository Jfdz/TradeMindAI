package com.tradingsaas.tradingcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TradingCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingCoreServiceApplication.class, args);
    }
}
