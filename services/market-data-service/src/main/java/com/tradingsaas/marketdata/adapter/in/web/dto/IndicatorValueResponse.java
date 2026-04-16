package com.tradingsaas.marketdata.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record IndicatorValueResponse(
        TechnicalIndicatorType type,
        BigDecimal value,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        Map<String, String> metadata) {
}
