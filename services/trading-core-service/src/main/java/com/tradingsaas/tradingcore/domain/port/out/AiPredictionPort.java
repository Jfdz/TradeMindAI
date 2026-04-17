package com.tradingsaas.tradingcore.domain.port.out;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;

public interface AiPredictionPort {

    AiPrediction predict(String ticker);
}
