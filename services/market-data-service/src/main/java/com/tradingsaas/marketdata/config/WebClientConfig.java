package com.tradingsaas.marketdata.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient yahooFinanceWebClient(
            WebClient.Builder builder,
            @Value("${market-data.yahoo.base-url}") String baseUrl,
            @Value("${market-data.yahoo.timeout-seconds:10}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(timeoutSeconds));
        return builder.baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
