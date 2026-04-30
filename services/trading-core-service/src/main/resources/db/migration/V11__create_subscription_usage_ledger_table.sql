CREATE TABLE IF NOT EXISTS trading_core.subscription_usage_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES trading_core.users(id),
    subscription_plan VARCHAR(20) NOT NULL,
    feature_key VARCHAR(64) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    response_status INTEGER NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_subscription_usage_ledger_user_occurred_at
    ON trading_core.subscription_usage_ledger (user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_subscription_usage_ledger_feature_occurred_at
    ON trading_core.subscription_usage_ledger (feature_key, occurred_at DESC);
