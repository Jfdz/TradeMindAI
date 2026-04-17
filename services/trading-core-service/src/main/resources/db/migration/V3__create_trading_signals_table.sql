CREATE TABLE IF NOT EXISTS trading_core.trading_signals (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol_id     UUID          NOT NULL,
    signal_type   VARCHAR(10)   NOT NULL CHECK (signal_type IN ('BUY', 'SELL', 'HOLD')),
    confidence    NUMERIC(5,4)  NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    timeframe     VARCHAR(20)   NOT NULL CHECK (timeframe IN (
        'MINUTE_1',
        'MINUTE_5',
        'MINUTE_15',
        'MINUTE_30',
        'HOUR_1',
        'DAILY',
        'WEEKLY',
        'MONTHLY'
    )),
    generated_at  TIMESTAMPTZ   NOT NULL,
    stop_loss_pct NUMERIC(5,2),
    take_profit_pct NUMERIC(5,2)
);

CREATE INDEX IF NOT EXISTS idx_trading_signals_symbol_generated_at
    ON trading_core.trading_signals(symbol_id, generated_at DESC);

-- rollback: DROP TABLE trading_core.trading_signals;
