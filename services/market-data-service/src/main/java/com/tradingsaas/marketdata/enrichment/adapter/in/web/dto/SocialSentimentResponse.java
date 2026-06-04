package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;

public record SocialSentimentResponse(
        String ticker, int positiveMentions, int negativeMentions, int totalMentions) {

    public static SocialSentimentResponse from(SocialSentiment sentiment) {
        return new SocialSentimentResponse(
                sentiment.ticker(),
                sentiment.positiveMentions(),
                sentiment.negativeMentions(),
                sentiment.totalMentions());
    }
}
