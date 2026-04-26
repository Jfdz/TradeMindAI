ALTER TABLE trading_core.users
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

CREATE TABLE IF NOT EXISTS trading_core.user_notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES trading_core.users(id) ON DELETE CASCADE,
    signal_digest BOOLEAN NOT NULL DEFAULT TRUE,
    live_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    risk_warnings BOOLEAN NOT NULL DEFAULT TRUE,
    strategy_changes BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_recap BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
