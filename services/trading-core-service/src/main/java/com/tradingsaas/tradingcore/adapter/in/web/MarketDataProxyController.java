package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.LatestPricesResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketDataPage;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketPriceResponse;
import com.tradingsaas.tradingcore.adapter.out.marketdata.MarketDataServiceAdapter.MarketSymbolResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class MarketDataProxyController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MarketDataServiceAdapter marketDataServiceAdapter;

    MarketDataProxyController(MarketDataServiceAdapter marketDataServiceAdapter) {
        this.marketDataServiceAdapter = marketDataServiceAdapter;
    }

    @GetMapping("/prices/{ticker}/latest")
    ResponseEntity<MarketPriceResponse> latestPrice(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "DAILY") String timeframe) {
        return marketDataServiceAdapter.fetchLatestPrice(ticker, timeframe)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/prices/latest")
    LatestPricesResponse latestPrices(
            @RequestParam List<String> tickers,
            @RequestParam(defaultValue = "DAILY") String timeframe) {
        return marketDataServiceAdapter.fetchLatestPrices(tickers, timeframe);
    }

    @GetMapping("/prices/{ticker}/history")
    ResponseEntity<MarketDataPage<MarketPriceResponse>> historicalPrices(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "DAILY") String timeframe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return ResponseEntity.ok(marketDataServiceAdapter.fetchHistoricalPrices(ticker, timeframe, from, to, page, clampedSize));
    }

    @GetMapping("/symbols")
    MarketDataPage<MarketSymbolResponse> symbols(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return marketDataServiceAdapter.fetchSymbols(page, clampedSize);
    }
}
