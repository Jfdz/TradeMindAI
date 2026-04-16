package com.tradingsaas.marketdata.adapter.in.web;

import com.tradingsaas.marketdata.adapter.in.web.dto.OhlcvResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.PagedResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.StockPriceResponse;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.in.GetHistoricalPricesUseCase;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
public class StockPricesController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GetHistoricalPricesUseCase getHistoricalPricesUseCase;

    public StockPricesController(GetHistoricalPricesUseCase getHistoricalPricesUseCase) {
        this.getHistoricalPricesUseCase = getHistoricalPricesUseCase;
    }

    @GetMapping("/{ticker}/history")
    public ResponseEntity<PagedResponse<StockPriceResponse>> getHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "DAILY") TimeFrame timeframe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }

        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, clampedSize, Sort.by("date").descending());

        Page<StockPriceResponse> result = getHistoricalPricesUseCase
                .getHistoricalPrices(ticker, timeframe, from, to, pageable)
                .map(this::toResponse);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private StockPriceResponse toResponse(StockPrice price) {
        return new StockPriceResponse(
                price.symbol().ticker(),
                price.date(),
                price.timeFrame(),
                new OhlcvResponse(
                        price.ohlcv().open(),
                        price.ohlcv().high(),
                        price.ohlcv().low(),
                        price.ohlcv().close(),
                        price.ohlcv().volume()),
                price.adjustedClose());
    }
}
