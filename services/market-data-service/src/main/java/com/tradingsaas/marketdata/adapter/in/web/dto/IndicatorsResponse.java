package com.tradingsaas.marketdata.adapter.in.web.dto;

import java.util.List;

public record IndicatorsResponse(String ticker, List<IndicatorValueResponse> indicators) {
}
