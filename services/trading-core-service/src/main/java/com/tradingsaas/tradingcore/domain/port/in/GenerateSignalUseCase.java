package com.tradingsaas.tradingcore.domain.port.in;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import java.util.UUID;

public interface GenerateSignalUseCase {

    TradingSignal generate(UUID symbolId, String ticker);

    TradingSignal generate(UUID symbolId, AiPrediction prediction);
}
