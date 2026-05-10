package com.tradingsaas.marketdata.enrichment.domain.model;

import java.time.Instant;
import java.util.Objects;

public record NewsItem(
        long id,
        String headline,
        Instant publishedAt,
        String category,
        String source,
        String summary,
        String url,
        String image) {

    public NewsItem {
        Objects.requireNonNull(headline, "headline must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
