CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE trading_core.positions
    ALTER COLUMN id SET DEFAULT gen_random_uuid();
