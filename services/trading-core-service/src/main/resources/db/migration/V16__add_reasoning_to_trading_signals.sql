ALTER TABLE trading_core.trading_signals
    ADD COLUMN IF NOT EXISTS reasoning              TEXT,
    ADD COLUMN IF NOT EXISTS reasoning_status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS reasoning_generated_at TIMESTAMPTZ;
