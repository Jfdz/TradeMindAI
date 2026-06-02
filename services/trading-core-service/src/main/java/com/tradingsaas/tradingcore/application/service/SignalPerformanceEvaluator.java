package com.tradingsaas.tradingcore.application.service;

import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Pure (no I/O) computation of a {@link SignalPerformance} snapshot from the
 * daily bars that followed a signal's generation.
 *
 * <ul>
 *   <li><b>outcome</b> — first-touch of target vs stop. BUY wins if a bar's high
 *       reaches {@code targetPrice} before any bar's low reaches {@code stopLoss};
 *       LOSS if stop touched first. A bar that straddles both resolves to LOSS
 *       (conservative). SELL is inverted. Scans every supplied bar, so a signal
 *       can resolve at any age; until touched it stays OPEN.</li>
 *   <li><b>max_profit / max_drawdown</b> — direction-aware signed fractions over
 *       bars within 30 calendar days of generation.</li>
 *   <li><b>price_Nd</b> — close of the first bar on or after generation + N
 *       calendar days (markets skip weekends/holidays); null until that much
 *       history exists.</li>
 * </ul>
 */
@Service
public class SignalPerformanceEvaluator {

    private static final int EXCURSION_HORIZON_DAYS = 30;
    private static final int PRICE_SCALE = 6;
    private static final int RETURN_SCALE = 4;

    private final Clock clock;

    public SignalPerformanceEvaluator(Clock clock) {
        this.clock = clock;
    }

    public SignalPerformance evaluate(
            UUID signalId,
            String ticker,
            SignalType signalType,
            Instant generatedAt,
            BigDecimal entryPrice,
            BigDecimal targetPrice,
            BigDecimal stopLoss,
            List<OhlcvBar> bars) {

        List<OhlcvBar> after = bars == null ? List.of() : bars.stream()
                .filter(b -> b.timestamp().isAfter(generatedAt))
                .sorted(Comparator.comparing(OhlcvBar::timestamp))
                .toList();

        BigDecimal price1d = priceAtOffset(after, generatedAt, 1);
        BigDecimal price3d = priceAtOffset(after, generatedAt, 3);
        BigDecimal price7d = priceAtOffset(after, generatedAt, 7);
        BigDecimal price30d = priceAtOffset(after, generatedAt, 30);

        Excursion excursion = excursion(after, generatedAt, signalType, entryPrice);
        Touch touch = firstTouch(after, signalType, targetPrice, stopLoss);

        return new SignalPerformance(
                signalId,
                ticker,
                generatedAt,
                entryPrice,
                price1d,
                price3d,
                price7d,
                price30d,
                excursion.maxProfit,
                excursion.maxDrawdown,
                touch.outcome,
                touch.resolvedAt,
                clock.instant());
    }

    private BigDecimal priceAtOffset(List<OhlcvBar> bars, Instant generatedAt, int calendarDays) {
        Instant target = generatedAt.plus(Duration.ofDays(calendarDays));
        // Bars are daily at start-of-day UTC; match by date >= generation + N days.
        var targetDate = target.atZone(ZoneOffset.UTC).toLocalDate();
        for (OhlcvBar bar : bars) {
            var barDate = bar.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
            if (!barDate.isBefore(targetDate)) {
                return BigDecimal.valueOf(bar.close()).setScale(PRICE_SCALE, RoundingMode.HALF_EVEN);
            }
        }
        return null;
    }

    private Excursion excursion(List<OhlcvBar> bars, Instant generatedAt, SignalType type, BigDecimal entryPrice) {
        if (entryPrice == null || entryPrice.signum() == 0 || type == SignalType.HOLD) {
            return new Excursion(null, null);
        }
        Instant horizonEnd = generatedAt.plus(EXCURSION_HORIZON_DAYS, ChronoUnit.DAYS);
        double entry = entryPrice.doubleValue();
        Double best = null;   // max favourable fraction
        Double worst = null;  // min (most adverse) fraction
        for (OhlcvBar bar : bars) {
            if (bar.timestamp().isAfter(horizonEnd)) {
                break;
            }
            double favourable;
            double adverse;
            if (type == SignalType.BUY) {
                favourable = (bar.high() - entry) / entry;
                adverse = (bar.low() - entry) / entry;
            } else { // SELL gains as price falls
                favourable = (entry - bar.low()) / entry;
                adverse = (entry - bar.high()) / entry;
            }
            best = best == null ? favourable : Math.max(best, favourable);
            worst = worst == null ? adverse : Math.min(worst, adverse);
        }
        return new Excursion(toFraction(best), toFraction(worst));
    }

    private Touch firstTouch(List<OhlcvBar> bars, SignalType type, BigDecimal targetPrice, BigDecimal stopLoss) {
        if (type == SignalType.HOLD || targetPrice == null || stopLoss == null) {
            return new Touch(SignalOutcome.OPEN, null);
        }
        double target = targetPrice.doubleValue();
        double stop = stopLoss.doubleValue();
        for (OhlcvBar bar : bars) {
            boolean stopHit;
            boolean targetHit;
            if (type == SignalType.BUY) {
                stopHit = bar.low() <= stop;
                targetHit = bar.high() >= target;
            } else { // SELL
                stopHit = bar.high() >= stop;
                targetHit = bar.low() <= target;
            }
            if (stopHit) { // straddle resolves to LOSS (conservative)
                return new Touch(SignalOutcome.LOSS, bar.timestamp());
            }
            if (targetHit) {
                return new Touch(SignalOutcome.WIN, bar.timestamp());
            }
        }
        return new Touch(SignalOutcome.OPEN, null);
    }

    private BigDecimal toFraction(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(RETURN_SCALE, RoundingMode.HALF_EVEN);
    }

    private record Excursion(BigDecimal maxProfit, BigDecimal maxDrawdown) {}

    private record Touch(SignalOutcome outcome, Instant resolvedAt) {}
}
