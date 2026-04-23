CREATE TABLE IF NOT EXISTS trading_core.strategies (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES trading_core.users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    stop_loss_pct NUMERIC(5,2) NOT NULL,
    take_profit_pct NUMERIC(5,2) NOT NULL,
    max_position_pct NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_strategies_user_created_at
    ON trading_core.strategies (user_id, created_at DESC);
