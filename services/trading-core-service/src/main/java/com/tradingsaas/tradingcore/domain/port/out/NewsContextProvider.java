package com.tradingsaas.tradingcore.domain.port.out;

import java.time.Instant;
import java.util.List;

public interface NewsContextProvider {

    List<NewsHeadline> fetchHeadlines(String ticker);

    record NewsHeadline(String title, String source, Instant publishedAt) {}
}
