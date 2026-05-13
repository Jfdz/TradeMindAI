package com.tradingsaas.marketdata.enrichment.adapter.in.web;

import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.NewsItemResponse;
import com.tradingsaas.marketdata.enrichment.domain.service.NewsAggregatorService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entrypoint for the multi-provider news aggregator. Consumers
 * (trading-core proxy, web-app via the proxy, ai-engine via trading-core)
 * use this endpoint instead of any single provider when they need the
 * full deduplicated, sorted view across providers.
 */
@RestController
@RequestMapping("/api/v1/news-aggregated")
public class NewsAggregatorController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final NewsAggregatorService aggregator;

    public NewsAggregatorController(NewsAggregatorService aggregator) {
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator must not be null");
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<List<NewsItemResponse>> getAggregatedTickerNews(
            @PathVariable String ticker,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "20") int limit) {

        Instant fromInstant;
        Instant toInstant;
        try {
            fromInstant = Instant.parse(from);
            toInstant = Instant.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        if (fromInstant.isAfter(toInstant)) {
            return ResponseEntity.badRequest().build();
        }
        int clamped = Math.min(Math.max(1, limit), MAX_LIMIT);

        List<NewsItemResponse> body = aggregator.aggregateTickerNews(ticker, fromInstant, toInstant, clamped).stream()
                .map(NewsItemResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }
}
