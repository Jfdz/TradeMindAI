package com.tradingsaas.marketdata.enrichment.domain.port.out;

import com.tradingsaas.marketdata.enrichment.domain.model.AnalystRecommendation;
import com.tradingsaas.marketdata.enrichment.domain.model.CompanyProfile;
import com.tradingsaas.marketdata.enrichment.domain.model.EarningsEvent;
import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;
import com.tradingsaas.marketdata.enrichment.domain.model.MacroContext;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;
import java.time.Instant;
import java.util.List;

public interface MarketEnrichmentProvider {

    CompanyProfile fetchProfile(String ticker);

    List<NewsItem> fetchMarketNews(String category, int limit);

    List<NewsItem> fetchTickerNews(String ticker, Instant from, Instant to, int limit);

    List<EarningsEvent> fetchEarnings(String ticker);

    List<AnalystRecommendation> fetchRecommendations(String ticker);

    List<String> fetchPeers(String ticker);

    InsiderActivity fetchInsiderActivity(String ticker);

    SocialSentiment fetchSocialSentiment(String ticker);

    MacroContext fetchMacroContext();
}
