package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.InsiderActivity;

public record InsiderActivityResponse(String ticker, int buyCount, int sellCount, long netShares) {

    public static InsiderActivityResponse from(InsiderActivity activity) {
        return new InsiderActivityResponse(
                activity.ticker(), activity.buyCount(), activity.sellCount(), activity.netShares());
    }
}
