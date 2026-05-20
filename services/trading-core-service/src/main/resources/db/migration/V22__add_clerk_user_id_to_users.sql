ALTER TABLE trading_core.users ADD COLUMN IF NOT EXISTS clerk_user_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_clerk_user_id
    ON trading_core.users(clerk_user_id)
    WHERE clerk_user_id IS NOT NULL;

ALTER TABLE trading_core.users ALTER COLUMN password_hash DROP NOT NULL;
