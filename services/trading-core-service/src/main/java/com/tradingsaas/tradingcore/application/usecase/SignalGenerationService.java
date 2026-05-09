package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GenerateSignalUseCase;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class SignalGenerationService implements GenerateSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);
    private static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_TAKE_PROFIT_PCT = new BigDecimal("4.00");

    private final TradingSignalRepository tradingSignalRepository;
    private final HistoricalMarketDataPort marketDataPort;

    SignalGenerationService(TradingSignalRepository tradingSignalRepository,
                            HistoricalMarketDataPort marketDataPort) {
        this.tradingSignalRepository = tradingSignalRepository;
        this.marketDataPort = marketDataPort;
    }

    @Override
    public TradingSignal generate(UUID symbolId, AiPrediction prediction) {
        BigDecimal entryPrice = fetchLatestPrice(prediction.getTicker());
        TradingSignal signal = new TradingSignal(
                UUID.randomUUID(),
                symbolId,
                prediction.getTicker(),
                prediction.getSignalType(),
                prediction.getConfidence(),
                Timeframe.DAILY,
                Instant.now(),
                riskStopLossPct(prediction.getSignalType()),
                riskTakeProfitPct(prediction.getSignalType()),
                prediction.getPredictedChangePct(),
                entryPrice);
        return tradingSignalRepository.save(signal);
    }

    private BigDecimal fetchLatestPrice(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        try {
            Map<String, BigDecimal> prices = marketDataPort.loadLatestPrices(List.of(ticker));
            return prices.get(ticker);
        } catch (Exception e) {
            log.warn("Could not fetch entry price for ticker={}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private BigDecimal riskStopLossPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_STOP_LOSS_PCT;
    }

    private BigDecimal riskTakeProfitPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_TAKE_PROFIT_PCT;
    }
}
