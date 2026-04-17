CREATE TABLE IF NOT EXISTS trading_core.subscriptions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES trading_core.users(id) ON DELETE CASCADE,
    plan        VARCHAR(20)  NOT NULL CHECK (plan IN ('FREE', 'BASIC', 'PREMIUM')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON trading_core.subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan    ON trading_core.subscriptions(plan);

-- rollback: DROP TABLE trading_core.subscriptions;
