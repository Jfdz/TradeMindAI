package com.tradingsaas.tradingcore.domain.port.out;

import java.math.BigDecimal;

public interface ReasoningGenerator {

    String generate(ReasoningContext context);

    record ReasoningContext(
            String ticker,
            String signalType,
            BigDecimal confidence,
            String newsContext) {}

    class AllLlmAdaptersExhaustedException extends RuntimeException {
        public AllLlmAdaptersExhaustedException(String message) { super(message); }
    }
}
