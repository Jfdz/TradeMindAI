CREATE TABLE IF NOT EXISTS market_data.market_data_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    symbol_ticker VARCHAR(32) NOT NULL,
    time_frame VARCHAR(16) NOT NULL,
    range_from DATE NOT NULL,
    range_to DATE NOT NULL,
    price_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_market_data_outbox_unpublished_created_at
    ON market_data.market_data_outbox (published_at, created_at);
