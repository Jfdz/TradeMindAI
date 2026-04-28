CREATE TABLE IF NOT EXISTS trading_core.backtest_jobs (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_backtest_jobs_created_at
    ON trading_core.backtest_jobs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_backtest_jobs_status
    ON trading_core.backtest_jobs (status);
