package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.AnalystConsensus;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.InsiderActivity;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.MacroContext;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.RecentPerformance;
import com.tradingsaas.tradingcore.adapter.in.web.dto.ReasoningContextResponse.SocialSentiment;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.AnalystRecommendationResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.InsufficientHistoryUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataUpstreamException;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.PriceFactsResponse;
import com.tradingsaas.tradingcore.domain.model.RecentTickerPerformance;
import com.tradingsaas.tradingcore.domain.port.out.SignalPerformanceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    // C1.5 — token-cost cap. ai-engine's BuildReasoningContextUseCase
    // already requests 24h/4 items; these defaults + hard MAX bound any
    // caller that omits the params so the LLM prompt can't balloon.
    static final int DEFAULT_NEWS_HOURS = 24;
    static final int DEFAULT_NEWS_LIMIT = 4;
    static final int MAX_NEWS_LIMIT = 4;
    // Window of most-recent resolved same-ticker signals summarised into the
    // reasoning context as the deterministic "reflection" track record.
    static final int RECENT_PERFORMANCE_LIMIT = 20;

    private final MarketDataServiceAdapter marketDataAdapter;
    private final EnrichmentServiceAdapter enrichmentAdapter;
    private final SignalPerformanceRepository performanceRepository;
    private final Clock clock;

    public ReasoningContextController(
            MarketDataServiceAdapter marketDataAdapter,
            EnrichmentServiceAdapter enrichmentAdapter,
            SignalPerformanceRepository performanceRepository,
            Clock clock) {
        this.marketDataAdapter = Objects.requireNonNull(marketDataAdapter, "marketDataAdapter must not be null");
        this.enrichmentAdapter = Objects.requireNonNull(enrichmentAdapter, "enrichmentAdapter must not be null");
        this.performanceRepository =
                Objects.requireNonNull(performanceRepository, "performanceRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<ReasoningContextResponse> getReasoningContext(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "24") int newsHours,
            @RequestParam(defaultValue = "4") int newsLimit) {

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

        // Best-effort analyst-consensus enrichment. Fail-soft like news, but an
        // empty result is NOT a degradation (no error tag) — it is supplementary,
        // not part of the grounded numeric core, so its absence must not pollute
        // the partial-outcome signal the caller logs.
        AnalystConsensus analystConsensus = null;
        try {
            analystConsensus = latestConsensus(enrichmentAdapter.fetchRecommendations(ticker));
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.analyst_recs_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            errors.add("analyst_recs_unavailable");
        }

        // Best-effort recent-performance reflection: the ticker's recent resolved
        // win/loss track record. Same fail-soft posture — an empty history is not
        // a degradation; only a query failure tags an error.
        RecentPerformance recentPerformance = null;
        try {
            RecentTickerPerformance perf = performanceRepository.recentPerformanceForTicker(
                    ticker.toUpperCase(), RECENT_PERFORMANCE_LIMIT);
            if (perf.hasHistory()) {
                recentPerformance =
                        new RecentPerformance(perf.wins(), perf.losses(), perf.resolvedCount());
            }
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.recent_performance_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            errors.add("recent_performance_unavailable");
        }

        // Best-effort insider-activity enrichment (Fase 4). Same fail-soft
        // posture — an empty result is supplementary, not a degradation; only a
        // provider failure tags an error.
        InsiderActivity insiderActivity = null;
        try {
            insiderActivity = enrichmentAdapter.fetchInsiderActivity(ticker)
                    .filter(r -> r.buyCount() > 0 || r.sellCount() > 0)
                    .map(r -> new InsiderActivity(r.buyCount(), r.sellCount(), r.netShares()))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.insider_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            errors.add("insider_unavailable");
        }

        // Best-effort social-sentiment enrichment (Fase 4). Same fail-soft
        // posture; integer mention counts are validator-safe downstream.
        SocialSentiment socialSentiment = null;
        try {
            socialSentiment = enrichmentAdapter.fetchSocialSentiment(ticker)
                    .filter(r -> r.totalMentions() > 0)
                    .map(r -> new SocialSentiment(
                            r.positiveMentions(), r.negativeMentions(), r.totalMentions()))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.sentiment_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            errors.add("sentiment_unavailable");
        }

        // Best-effort market-wide macro context (Fase 4). Not ticker-specific;
        // the same snapshot attaches to every context. Fail-soft, integer count.
        MacroContext macroContext = null;
        try {
            macroContext = enrichmentAdapter.fetchMacroContext()
                    .filter(r -> r.recentMarketNewsCount() > 0)
                    .map(r -> new MacroContext(r.recentMarketNewsCount()))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn(
                    "event=reasoning_context.macro_failed ticker={} message={}",
                    ticker,
                    e.getMessage());
            errors.add("macro_unavailable");
        }

        return ResponseEntity.ok(new ReasoningContextResponse(
                ticker.toUpperCase(),
                now,
                priceFacts.get(),
                news,
                analystConsensus,
                recentPerformance,
                insiderActivity,
                socialSentiment,
                macroContext,
                List.copyOf(errors)));
    }

    /**
     * Reduce the provider's recommendation history to the latest snapshot.
     * Picks the entry with the most recent {@code period}; returns null when
     * there is nothing to summarise.
     */
    private static AnalystConsensus latestConsensus(List<AnalystRecommendationResponse> recs) {
        if (recs == null || recs.isEmpty()) {
            return null;
        }
        AnalystRecommendationResponse latest = recs.stream()
                .filter(r -> r.period() != null)
                .max(Comparator.comparing(AnalystRecommendationResponse::period))
                .orElse(recs.get(0));
        int total =
                latest.strongBuy() + latest.buy() + latest.hold() + latest.sell() + latest.strongSell();
        return new AnalystConsensus(
                latest.period() == null ? null : latest.period().toString(),
                latest.strongBuy(),
                latest.buy(),
                latest.hold(),
                latest.sell(),
                latest.strongSell(),
                total);
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
