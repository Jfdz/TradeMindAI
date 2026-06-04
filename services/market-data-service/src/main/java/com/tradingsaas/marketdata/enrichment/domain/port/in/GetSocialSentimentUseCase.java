package com.tradingsaas.marketdata.enrichment.domain.port.in;

import com.tradingsaas.marketdata.enrichment.domain.model.SocialSentiment;

public interface GetSocialSentimentUseCase {

    SocialSentiment getSocialSentiment(String ticker);
}
