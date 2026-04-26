package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalsUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
class SignalController {

    private final GetSignalsUseCase getSignalsUseCase;

    SignalController(GetSignalsUseCase getSignalsUseCase) {
        this.getSignalsUseCase = getSignalsUseCase;
    }

    @GetMapping
    Page<SignalResponse> listSignals(@PageableDefault(sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return getSignalsUseCase.getSignals(pageable).map(SignalResponse::fromDomain);
    }

    @GetMapping("/latest")
    SignalResponse getLatest() {
        return getSignalsUseCase.getLatest()
                .map(SignalResponse::fromDomain)
                .orElseThrow(() -> new SignalNotFoundException("No trading signals found"));
    }

    @GetMapping("/{id}")
    SignalResponse getById(@PathVariable UUID id) {
        return getSignalsUseCase.getById(id)
                .map(SignalResponse::fromDomain)
                .orElseThrow(() -> new SignalNotFoundException("Signal not found: " + id));
    }

    record SignalResponse(
            UUID id,
            String symbol,
            String type,
            BigDecimal confidence,
            String timeframe,
            Instant generatedAt,
            BigDecimal stopLossPct,
            BigDecimal takeProfitPct,
            BigDecimal predictedChangePct) {

        static SignalResponse fromDomain(TradingSignal signal) {
            return new SignalResponse(
                    signal.getId(),
                    signal.getTicker() != null ? signal.getTicker() : signal.getSymbolId().toString(),
                    signal.getType().name(),
                    signal.getConfidence().getValue(),
                    signal.getTimeframe().name(),
                    signal.getGeneratedAt(),
                    signal.getStopLossPct(),
                    signal.getTakeProfitPct(),
                    signal.getPredictedChangePct());
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class SignalNotFoundException extends RuntimeException {
        SignalNotFoundException(String message) {
            super(message);
        }
    }
}
