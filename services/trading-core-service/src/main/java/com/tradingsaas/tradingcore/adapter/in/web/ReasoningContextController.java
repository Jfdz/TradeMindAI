package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.InsufficientHistoryUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service BFF: assembles a {@code ReasoningContext} for the
 * ai-engine by combining {@code PriceFacts} and aggregated news.
 *
 * <p>Auth: guarded by {@code InternalSecretFilter} via the
 * {@code /api/v1/internal/**} prefix. JWT is not required and not parsed.
 */
@RestController
@RequestMapping("/api/v1/internal/reasoning-context")
public class ReasoningContextController {

    private static final Logger log = LoggerFactory.getLogger(ReasoningContextController.class);
    static final int DEFAULT_NEWS_HOURS = 48;
    static final int DEFAULT_NEWS_LIMIT = 8;
    static final int MAX_NEWS_LIMIT = 25;

    private final MarketDataServiceAdapter marketDataAdapter;
    private final EnrichmentServiceAdapter enrichmentAdapter;
    private final Clock clock;

    public ReasoningContextController(
            MarketDataServiceAdapter marketDataAdapter,
            EnrichmentServiceAdapter enrichmentAdapter,
            Clock clock) {
        this.marketDataAdapter = Objects.requireNonNull(marketDataAdapter, "marketDataAdapter must not be null");
        this.enrichmentAdapter = Objects.requireNonNull(enrichmentAdapter, "enrichmentAdapter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<ReasoningContextResponse> getReasoningContext(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "48") int newsHours,
            @RequestParam(defaultValue = "8") int newsLimit) {

        int hours = Math.max(1, Math.min(newsHours, 7 * 24));
        int limit = Math.max(1, Math.min(newsLimit, MAX_NEWS_LIMIT));

        Optional<PriceFactsResponse> priceFacts = marketDataAdapter.fetchPriceFacts(ticker);
        if (priceFacts.isEmpty()) {
            log.info("event=reasoning_context.ticker_not_tracked ticker={}", ticker);
            return ResponseEntity.notFound().build();
        }

        Instant now = Instant.now(clock);
        Instant to = now;
        Instant from = now.minus(Duration.ofHours(hours));

        List<String> errors = new ArrayList<>();
        List<NewsItemResponse> news;
        try {
            news = enrichmentAdapter.fetchAggregatedTickerNews(ticker, from, to, limit);
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.news_aggregator_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            news = List.of();
            errors.add("news_aggregator_unavailable");
        }
        if (news.isEmpty() && errors.isEmpty()) {
            errors.add("news_aggregator_empty");
        }

        return ResponseEntity.ok(new ReasoningContextResponse(
                ticker.toUpperCase(),
                now,
                priceFacts.get(),
                news,
                List.copyOf(errors)));
    }

    @ExceptionHandler(InsufficientHistoryUpstreamException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientHistory(InsufficientHistoryUpstreamException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of(
                        "error", "INSUFFICIENT_HISTORY",
                        "ticker", ex.ticker()));
    }

    @ExceptionHandler(MarketDataUpstreamException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamFailure(MarketDataUpstreamException ex) {
        log.warn("event=reasoning_context.market_data_upstream_failed message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "MARKET_DATA_UNAVAILABLE"));
    }
}
