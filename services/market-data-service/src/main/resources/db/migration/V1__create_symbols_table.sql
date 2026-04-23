CREATE TABLE IF NOT EXISTS market_data.symbols (
    ticker VARCHAR(16) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(64) NOT NULL,
    sector VARCHAR(128) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_symbols_active
    ON market_data.symbols (active);

CREATE INDEX IF NOT EXISTS idx_symbols_exchange
    ON market_data.symbols (exchange);
