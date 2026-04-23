package com.tradingsaas.marketdata.adapter.in.web;

import com.tradingsaas.marketdata.adapter.in.web.dto.IndicatorValueResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.IndicatorsResponse;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.port.in.GetIndicatorsUseCase;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorsController {

    private final GetIndicatorsUseCase getIndicatorsUseCase;

    public IndicatorsController(GetIndicatorsUseCase getIndicatorsUseCase) {
        this.getIndicatorsUseCase = getIndicatorsUseCase;
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<IndicatorsResponse> getLatestIndicators(
            @PathVariable String ticker,
            @RequestParam(required = false) List<TechnicalIndicatorType> types) {

        String normalizedTicker = ticker.toUpperCase();
        List<TechnicalIndicator> indicators = getIndicatorsUseCase.getLatestIndicators(normalizedTicker, types);

        if (indicators.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        IndicatorsResponse response = new IndicatorsResponse(
                normalizedTicker,
                indicators.stream()
                        .map(i -> new IndicatorValueResponse(i.type(), i.value(), i.date(), i.metadata()))
                        .toList());

        return ResponseEntity.ok(response);
    }
}
