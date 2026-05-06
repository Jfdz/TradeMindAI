ALTER TABLE trading_core.trading_signals
    ALTER COLUMN symbol_id DROP NOT NULL;

-- rollback: ALTER TABLE trading_core.trading_signals ALTER COLUMN symbol_id SET NOT NULL;
