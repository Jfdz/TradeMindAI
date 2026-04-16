package com.tradingsaas.marketdata.adapter.in.web;

import com.tradingsaas.marketdata.adapter.in.web.dto.PagedResponse;
import com.tradingsaas.marketdata.adapter.in.web.dto.SymbolResponse;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.in.GetSymbolsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/symbols")
public class SymbolsController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GetSymbolsUseCase getSymbolsUseCase;

    public SymbolsController(GetSymbolsUseCase getSymbolsUseCase) {
        this.getSymbolsUseCase = getSymbolsUseCase;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<SymbolResponse>> getSymbols(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, clampedSize, Sort.by("ticker").ascending());

        Page<SymbolResponse> result = getSymbolsUseCase
                .getSymbols(pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(PagedResponse.from(result));
    }

    private SymbolResponse toResponse(Symbol symbol) {
        return new SymbolResponse(symbol.ticker(), symbol.name(), symbol.exchange(), symbol.sector(), symbol.active());
    }
}
