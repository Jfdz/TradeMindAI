package com.tradingsaas.tradingcore.application.usecase;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GenerateSignalUseCase;
import com.tradingsaas.tradingcore.domain.port.out.AiPredictionPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SignalGenerationService implements GenerateSignalUseCase {

    private static final BigDecimal DEFAULT_STOP_LOSS_PCT = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_TAKE_PROFIT_PCT = new BigDecimal("4.00");

    private final AiPredictionPort aiPredictionPort;
    private final TradingSignalRepository tradingSignalRepository;

    SignalGenerationService(AiPredictionPort aiPredictionPort, TradingSignalRepository tradingSignalRepository) {
        this.aiPredictionPort = aiPredictionPort;
        this.tradingSignalRepository = tradingSignalRepository;
    }

    @Override
    public TradingSignal generate(UUID symbolId, String ticker) {
        AiPrediction prediction = aiPredictionPort.predict(ticker);
        return generate(symbolId, prediction);
    }

    @Override
    public TradingSignal generate(UUID symbolId, AiPrediction prediction) {
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
                prediction.getPredictedChangePct());
        return tradingSignalRepository.save(signal);
    }

    private BigDecimal riskStopLossPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_STOP_LOSS_PCT;
    }

    private BigDecimal riskTakeProfitPct(SignalType signalType) {
        return signalType == SignalType.HOLD ? null : DEFAULT_TAKE_PROFIT_PCT;
    }
}
