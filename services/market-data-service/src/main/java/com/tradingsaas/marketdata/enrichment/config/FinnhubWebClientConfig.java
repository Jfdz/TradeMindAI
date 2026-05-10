package com.tradingsaas.marketdata.enrichment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(FinnhubProperties.class)
public class FinnhubWebClientConfig {

    @Bean
    public WebClient finnhubWebClient(WebClient.Builder builder, FinnhubProperties props) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        return builder
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
