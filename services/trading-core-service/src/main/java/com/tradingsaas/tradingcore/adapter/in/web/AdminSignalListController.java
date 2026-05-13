package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.in.web.dto.AdminSignalSummary;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only listing endpoints feeding the E1 reasoning-audit explorer.
 *
 * <p>Both endpoints are guarded by {@code .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")}
 * in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/signals")
public class AdminSignalListController {

    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 100;

    private final TradingSignalRepository repository;

    public AdminSignalListController(TradingSignalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @GetMapping
    public ResponseEntity<Page<AdminSignalSummary>> list(
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int clampedPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        Page<AdminSignalSummary> result = repository
                .findAdminSignals(ticker, pageable)
                .map(AdminSignalSummary::from);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tickers")
    public ResponseEntity<List<String>> tickers() {
        return ResponseEntity.ok(repository.findDistinctTickers());
    }
}
