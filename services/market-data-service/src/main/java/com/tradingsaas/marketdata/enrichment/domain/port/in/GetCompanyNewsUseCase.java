package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import java.time.Instant;
import java.util.List;

public interface GetCompanyNewsUseCase {

    List<NewsItem> getMarketNews(String category, int limit);

    List<NewsItem> getTickerNews(String ticker, Instant from, Instant to, int limit);
}
