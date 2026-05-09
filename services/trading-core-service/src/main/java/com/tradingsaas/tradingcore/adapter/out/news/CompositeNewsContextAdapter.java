package com.tradingsaas.tradingcore.adapter.out.news;

import com.tradingsaas.tradingcore.domain.port.out.NewsContextProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RSS-only composite while Track A (Enrichment) is not yet on develop.
 * After Track A merges: add EnrichmentNewsContextAdapter as primary and
 * demote RSS to augmentation when fewer than 3 headlines found.
 */
@Component
public class CompositeNewsContextAdapter implements NewsContextProvider {

    private final GoogleNewsRssAdapter rssAdapter;

    public CompositeNewsContextAdapter(GoogleNewsRssAdapter rssAdapter) {
        this.rssAdapter = rssAdapter;
    }

    @Override
    public List<NewsHeadline> fetchHeadlines(String ticker) {
        return rssAdapter.fetchHeadlines(ticker);
    }
}
