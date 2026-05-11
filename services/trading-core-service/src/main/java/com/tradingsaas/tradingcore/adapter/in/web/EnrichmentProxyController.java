package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.AnalystRecommendationResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.CompanyProfileResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.EarningsEventResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.EnrichmentServiceAdapter.NewsItemResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrichment")
public class EnrichmentProxyController {

    private final EnrichmentServiceAdapter adapter;

    EnrichmentProxyController(EnrichmentServiceAdapter adapter) {
        this.adapter = adapter;
    }

    @GetMapping("/profile/{ticker}")
    public ResponseEntity<CompanyProfileResponse> getProfile(@PathVariable String ticker) {
        return adapter.fetchProfile(ticker)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/news")
    public ResponseEntity<List<NewsItemResponse>> getMarketNews(
            @RequestParam(defaultValue = "general") String category,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(adapter.fetchMarketNews(category, limit));
    }

    @GetMapping("/news/{ticker}")
    public ResponseEntity<List<NewsItemResponse>> getTickerNews(
            @PathVariable String ticker,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(adapter.fetchTickerNews(ticker, Instant.parse(from), Instant.parse(to), limit));
    }

    @GetMapping("/earnings/{ticker}")
    public ResponseEntity<List<EarningsEventResponse>> getEarnings(@PathVariable String ticker) {
        return ResponseEntity.ok(adapter.fetchEarnings(ticker));
    }

    @GetMapping("/recommendations/{ticker}")
    public ResponseEntity<List<AnalystRecommendationResponse>> getRecommendations(@PathVariable String ticker) {
        return ResponseEntity.ok(adapter.fetchRecommendations(ticker));
    }

    @GetMapping("/peers/{ticker}")
    public ResponseEntity<List<String>> getPeers(@PathVariable String ticker) {
        return ResponseEntity.ok(adapter.fetchPeers(ticker));
    }
}
