package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
class DataFreshnessController {

    private final TradingSignalJpaRepository signalRepository;

    DataFreshnessController(TradingSignalJpaRepository signalRepository) {
        this.signalRepository = signalRepository;
    }

    @GetMapping("/data-freshness")
    Map<String, Object> dataFreshness() {
        return signalRepository.findTopByOrderByGeneratedAtDesc()
                .map(signal -> {
                    Instant lastSignalAt = signal.getGeneratedAt();
                    double ageHours = Duration.between(lastSignalAt, Instant.now()).toMinutes() / 60.0;
                    String status = ageHours < 25 ? "FRESH" : ageHours < 48 ? "STALE" : "CRITICAL";
                    return Map.<String, Object>of(
                            "lastSignalAt", lastSignalAt.toString(),
                            "signalAgeHours", Math.round(ageHours * 10.0) / 10.0,
                            "status", status
                    );
                })
                .orElseGet(() -> Map.of(
                        "lastSignalAt", null,
                        "signalAgeHours", null,
                        "status", "CRITICAL"
                ));
    }
}
