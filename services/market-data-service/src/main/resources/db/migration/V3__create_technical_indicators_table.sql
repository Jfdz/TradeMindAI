CREATE TABLE IF NOT EXISTS market_data.technical_indicators (
    id BIGSERIAL PRIMARY KEY,
    symbol_ticker VARCHAR(16) NOT NULL,
    indicator_date DATE NOT NULL,
    indicator_type VARCHAR(32) NOT NULL,
    value NUMERIC(19, 6) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_technical_indicators_symbol
        FOREIGN KEY (symbol_ticker)
        REFERENCES market_data.symbols (ticker)
        ON DELETE CASCADE,
    CONSTRAINT uq_technical_indicators_symbol_date_type
        UNIQUE (symbol_ticker, indicator_date, indicator_type)
);

CREATE INDEX IF NOT EXISTS idx_technical_indicators_symbol_ticker
    ON market_data.technical_indicators (symbol_ticker);

CREATE INDEX IF NOT EXISTS idx_technical_indicators_indicator_date
    ON market_data.technical_indicators (indicator_date DESC);

CREATE INDEX IF NOT EXISTS idx_technical_indicators_symbol_date
    ON market_data.technical_indicators (symbol_ticker, indicator_date DESC);
