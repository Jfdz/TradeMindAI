package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;

public record NewsItemResponse(
        long id,
        String headline,
        String publishedAt,
        String category,
        String source,
        String summary,
        String url,
        String image) {

    public static NewsItemResponse from(NewsItem item) {
        return new NewsItemResponse(
                item.id(),
                item.headline(),
                item.publishedAt().toString(),
                item.category(),
                item.source(),
                item.summary(),
                item.url(),
                item.image());
    }
}
