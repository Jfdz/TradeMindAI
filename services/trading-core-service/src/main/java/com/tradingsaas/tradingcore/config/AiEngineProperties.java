package com.tradingsaas.tradingcore.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading-core.ai-engine")
public class AiEngineProperties {

    private String serviceUrl = "http://localhost:8000";
    private Duration timeout = Duration.ofSeconds(5);

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
