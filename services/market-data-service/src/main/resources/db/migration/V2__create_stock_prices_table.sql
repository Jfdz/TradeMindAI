CREATE TABLE IF NOT EXISTS market_data.stock_prices (
    id BIGSERIAL PRIMARY KEY,
    symbol_ticker VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    time_frame VARCHAR(16) NOT NULL,
    open NUMERIC(19, 6) NOT NULL,
    high NUMERIC(19, 6) NOT NULL,
    low NUMERIC(19, 6) NOT NULL,
    close NUMERIC(19, 6) NOT NULL,
    adjusted_close NUMERIC(19, 6) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_prices_symbol
        FOREIGN KEY (symbol_ticker)
        REFERENCES market_data.symbols (ticker)
        ON DELETE CASCADE,
    CONSTRAINT uq_stock_prices_symbol_date_timeframe
        UNIQUE (symbol_ticker, trade_date, time_frame),
    CONSTRAINT ck_stock_prices_volume_non_negative
        CHECK (volume >= 0),
    CONSTRAINT ck_stock_prices_price_range
        CHECK (high >= low)
);

CREATE INDEX IF NOT EXISTS idx_stock_prices_symbol_ticker
    ON market_data.stock_prices (symbol_ticker);

CREATE INDEX IF NOT EXISTS idx_stock_prices_trade_date
    ON market_data.stock_prices (trade_date DESC);

CREATE INDEX IF NOT EXISTS idx_stock_prices_symbol_date
    ON market_data.stock_prices (symbol_ticker, trade_date DESC);
