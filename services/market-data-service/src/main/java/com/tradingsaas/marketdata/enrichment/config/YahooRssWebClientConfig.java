package com.tradingsaas.marketdata.enrichment.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Spring config for the Yahoo RSS WebClient. The bean is only registered
 * when {@code market-data.yahoo-rss.enabled=true} — keeps the secondary
 * provider behind a flag until validated in staging.
 */
@Configuration
@EnableConfigurationProperties(YahooRssProperties.class)
@ConditionalOnProperty(prefix = "market-data.yahoo-rss", name = "enabled", havingValue = "true")
public class YahooRssWebClientConfig {

    @Bean
    public WebClient yahooRssWebClient(WebClient.Builder builder, YahooRssProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        WebClient.Builder b = builder
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        if (props.userAgent() != null && !props.userAgent().isBlank()) {
            b = b.defaultHeader("User-Agent", props.userAgent());
        }
        return b.build();
    }
}
