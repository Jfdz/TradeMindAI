CREATE TABLE IF NOT EXISTS trading_core.backtests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES trading_core.users(id) ON DELETE CASCADE,
    strategy_id UUID NOT NULL REFERENCES trading_core.strategies(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    request JSONB NOT NULL,
    results JSONB,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_backtests_user_created_at
    ON trading_core.backtests (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backtests_strategy_id
    ON trading_core.backtests (strategy_id);

CREATE INDEX IF NOT EXISTS idx_backtests_status
    ON trading_core.backtests (status);
