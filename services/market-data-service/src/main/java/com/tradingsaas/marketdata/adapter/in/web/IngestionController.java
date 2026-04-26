package com.tradingsaas.marketdata.adapter.in.web;

import com.tradingsaas.marketdata.application.usecase.ScheduledMarketDataIngestionJob;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestion")
class IngestionController {

    private final ScheduledMarketDataIngestionJob ingestionJob;

    IngestionController(ScheduledMarketDataIngestionJob ingestionJob) {
        this.ingestionJob = ingestionJob;
    }

    /**
     * Manually trigger market data ingestion for all tracked symbols.
     * Access is restricted to cluster-internal callers via K8s NetworkPolicy.
     * Returns 202 immediately; ingestion runs asynchronously in the background.
     */
    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> trigger() {
        CompletableFuture.runAsync(ingestionJob::run);
        return Map.of(
                "status", "TRIGGERED",
                "message", "Ingestion started asynchronously for all tracked symbols",
                "triggeredAt", Instant.now().toString()
        );
    }
}
