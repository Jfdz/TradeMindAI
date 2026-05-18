ALTER TABLE trading_core.trading_signals
    ADD COLUMN IF NOT EXISTS target_price      NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS stop_loss         NUMERIC(18,6),
    ADD COLUMN IF NOT EXISTS expected_move_pct NUMERIC(8,4);
