package com.tradingsaas.marketdata.enrichment.application.usecase;

import com.tradingsaas.marketdata.enrichment.config.FinnhubProperties;
import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import com.tradingsaas.marketdata.enrichment.domain.port.in.GetCompanyNewsUseCase;
import com.tradingsaas.marketdata.enrichment.domain.port.out.EnrichmentCache;
import com.tradingsaas.marketdata.enrichment.domain.port.out.MarketEnrichmentProvider;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class GetCompanyNewsUseCaseImpl implements GetCompanyNewsUseCase {

    private static final String MARKET_NEWS_PREFIX = "enrichment:market-news:";
    private static final String TICKER_NEWS_PREFIX = "enrichment:news:";

    private final MarketEnrichmentProvider provider;
    private final EnrichmentCache cache;
    private final FinnhubProperties properties;

    public GetCompanyNewsUseCaseImpl(
            MarketEnrichmentProvider provider,
            EnrichmentCache cache,
            FinnhubProperties properties) {
        this.provider = Objects.requireNonNull(provider);
        this.cache = Objects.requireNonNull(cache);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public List<NewsItem> getMarketNews(String category, int limit) {
        String key = MARKET_NEWS_PREFIX + category + ":" + limit;
        return cache.get(key, List.class).map(raw -> (List<NewsItem>) raw).orElseGet(() -> {
            List<NewsItem> news = provider.fetchMarketNews(category, limit);
            cache.put(key, news, properties.cache().newsTtl());
            return news;
        });
    }

    @Override
    public List<NewsItem> getTickerNews(String ticker, Instant from, Instant to, int limit) {
        String key = TICKER_NEWS_PREFIX + ticker.toUpperCase() + ":" + from.getEpochSecond()
                + ":" + to.getEpochSecond() + ":" + limit;
        return cache.get(key, List.class).map(raw -> (List<NewsItem>) raw).orElseGet(() -> {
            List<NewsItem> news = provider.fetchTickerNews(ticker, from, to, limit);
            cache.put(key, news, properties.cache().newsTtl());
            return news;
        });
    }
}
