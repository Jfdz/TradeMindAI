package com.tradingsaas.marketdata.adapter.in.web;

import com.tradingsaas.marketdata.adapter.in.web.dto.PriceFactsResponse;
import com.tradingsaas.marketdata.domain.exception.InsufficientHistoryException;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetPriceFactsUseCase;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/price-facts")
public class PriceFactsController {

    private final GetPriceFactsUseCase getPriceFactsUseCase;

    public PriceFactsController(GetPriceFactsUseCase getPriceFactsUseCase) {
        this.getPriceFactsUseCase = Objects.requireNonNull(getPriceFactsUseCase, "getPriceFactsUseCase must not be null");
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<PriceFactsResponse> getPriceFacts(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "DAILY") TimeFrame timeframe) {
        if (timeframe != TimeFrame.DAILY) {
            return ResponseEntity.badRequest().build();
        }
        Optional<PriceFacts> facts = getPriceFactsUseCase.getPriceFacts(ticker, timeframe);
        return facts.map(f -> ResponseEntity.ok(PriceFactsResponse.from(f)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(InsufficientHistoryException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientHistory(InsufficientHistoryException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of(
                        "error", "INSUFFICIENT_HISTORY",
                        "ticker", ex.ticker(),
                        "barsAvailable", ex.barsAvailable(),
                        "barsRequired", ex.barsRequired()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().build();
    }
}
