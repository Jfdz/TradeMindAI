ALTER TABLE trading_core.trading_signals
    ADD COLUMN IF NOT EXISTS ticker VARCHAR(32),
    ADD COLUMN IF NOT EXISTS predicted_change_pct NUMERIC(8,4);
