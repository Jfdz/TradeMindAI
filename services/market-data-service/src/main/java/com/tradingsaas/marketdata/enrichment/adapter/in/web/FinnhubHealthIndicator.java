package com.tradingsaas.marketdata.enrichment.adapter.in.web;

import com.tradingsaas.marketdata.enrichment.adapter.out.external.FinnhubAdapter;
import com.tradingsaas.marketdata.enrichment.domain.exception.EnrichmentUnavailableException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("finnhub")
public class FinnhubHealthIndicator implements HealthIndicator, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FinnhubHealthIndicator.class);

    private final FinnhubAdapter finnhubAdapter;
    private final AtomicReference<Health> cached = new AtomicReference<>(Health.unknown().build());

    public FinnhubHealthIndicator(FinnhubAdapter finnhubAdapter) {
        this.finnhubAdapter = finnhubAdapter;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!finnhubAdapter.isApiKeyPresent()) {
            log.error("event=finnhub.startup.failed reason=api_key_empty");
            cached.set(Health.down().withDetail("reason", "API key not configured").build());
            return;
        }
        try {
            finnhubAdapter.fetchProfile("AAPL");
            log.info("event=finnhub.startup.healthy");
            cached.set(Health.up().build());
        } catch (EnrichmentUnavailableException e) {
            log.error("event=finnhub.startup.failed reason={}", e.getReason());
            cached.set(Health.down().withDetail("reason", e.getReason()).build());
        } catch (Exception e) {
            log.error("event=finnhub.startup.failed reason=unknown", e);
            cached.set(Health.down().withDetail("reason", "startup probe failed").build());
        }
    }

    @Override
    public Health health() {
        return cached.get();
    }
}
