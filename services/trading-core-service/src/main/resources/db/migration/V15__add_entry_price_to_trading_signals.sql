ALTER TABLE trading_core.trading_signals
    ADD COLUMN IF NOT EXISTS entry_price NUMERIC(18, 6);
