package com.tradingsaas.marketdata.enrichment.adapter.in.web.dto;

import com.tradingsaas.marketdata.enrichment.domain.model.MacroContext;

public record MacroContextResponse(int recentMarketNewsCount) {

    public static MacroContextResponse from(MacroContext macro) {
        return new MacroContextResponse(macro.recentMarketNewsCount());
    }
}
