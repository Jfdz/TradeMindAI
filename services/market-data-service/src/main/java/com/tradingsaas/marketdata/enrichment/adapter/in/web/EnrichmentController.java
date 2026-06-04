package com.tradingsaas.marketdata.enrichment.adapter.in.web;

import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.AnalystRecommendationResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.CompanyProfileResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.EarningsEventResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.InsiderActivityResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.MacroContextResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.NewsItemResponse;
import com.tradingsaas.marketdata.enrichment.adapter.in.web.dto.SocialSentimentResponse;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyNewsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyProfileUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetEarningsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetInsiderActivityUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetMacroContextUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetPeersUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetRecommendationsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetSocialSentimentUseCase;
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
public class EnrichmentController {

    private final GetCompanyProfileUseCase profileUseCase;
    private final GetCompanyNewsUseCase newsUseCase;
    private final GetEarningsUseCase earningsUseCase;
    private final GetRecommendationsUseCase recommendationsUseCase;
    private final GetPeersUseCase peersUseCase;
    private final GetInsiderActivityUseCase insiderUseCase;
    private final GetSocialSentimentUseCase sentimentUseCase;
    private final GetMacroContextUseCase macroUseCase;

    public EnrichmentController(
            GetCompanyProfileUseCase profileUseCase,
            GetCompanyNewsUseCase newsUseCase,
            GetEarningsUseCase earningsUseCase,
            GetRecommendationsUseCase recommendationsUseCase,
            GetPeersUseCase peersUseCase,
            GetInsiderActivityUseCase insiderUseCase,
            GetSocialSentimentUseCase sentimentUseCase,
            GetMacroContextUseCase macroUseCase) {
        this.profileUseCase = profileUseCase;
        this.newsUseCase = newsUseCase;
        this.earningsUseCase = earningsUseCase;
        this.recommendationsUseCase = recommendationsUseCase;
        this.peersUseCase = peersUseCase;
        this.insiderUseCase = insiderUseCase;
        this.sentimentUseCase = sentimentUseCase;
        this.macroUseCase = macroUseCase;
    }

    @GetMapping("/profile/{ticker}")
    public ResponseEntity<CompanyProfileResponse> getProfile(@PathVariable String ticker) {
        return ResponseEntity.ok(CompanyProfileResponse.from(profileUseCase.getProfile(ticker)));
    }

    @GetMapping("/news")
    public ResponseEntity<List<NewsItemResponse>> getMarketNews(
            @RequestParam(defaultValue = "general") String category,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(newsUseCase.getMarketNews(category, limit).stream()
                .map(NewsItemResponse::from)
                .toList());
    }

    @GetMapping("/news/{ticker}")
    public ResponseEntity<List<NewsItemResponse>> getTickerNews(
            @PathVariable String ticker,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(
                newsUseCase.getTickerNews(ticker, Instant.parse(from), Instant.parse(to), limit).stream()
                        .map(NewsItemResponse::from)
                        .toList());
    }

    @GetMapping("/earnings/{ticker}")
    public ResponseEntity<List<EarningsEventResponse>> getEarnings(@PathVariable String ticker) {
        return ResponseEntity.ok(earningsUseCase.getEarnings(ticker).stream()
                .map(EarningsEventResponse::from)
                .toList());
    }

    @GetMapping("/recommendations/{ticker}")
    public ResponseEntity<List<AnalystRecommendationResponse>> getRecommendations(@PathVariable String ticker) {
        return ResponseEntity.ok(recommendationsUseCase.getRecommendations(ticker).stream()
                .map(AnalystRecommendationResponse::from)
                .toList());
    }

    @GetMapping("/peers/{ticker}")
    public ResponseEntity<List<String>> getPeers(@PathVariable String ticker) {
        return ResponseEntity.ok(peersUseCase.getPeers(ticker));
    }

    @GetMapping("/insider/{ticker}")
    public ResponseEntity<InsiderActivityResponse> getInsider(@PathVariable String ticker) {
        return ResponseEntity.ok(InsiderActivityResponse.from(insiderUseCase.getInsiderActivity(ticker)));
    }

    @GetMapping("/sentiment/{ticker}")
    public ResponseEntity<SocialSentimentResponse> getSentiment(@PathVariable String ticker) {
        return ResponseEntity.ok(SocialSentimentResponse.from(sentimentUseCase.getSocialSentiment(ticker)));
    }

    @GetMapping("/macro")
    public ResponseEntity<MacroContextResponse> getMacro() {
        return ResponseEntity.ok(MacroContextResponse.from(macroUseCase.getMacroContext()));
    }
}
