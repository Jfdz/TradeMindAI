CREATE TABLE IF NOT EXISTS trading_core.portfolios (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES trading_core.users(id) ON DELETE CASCADE,
    initial_capital NUMERIC(18,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_portfolios_user_id
    ON trading_core.portfolios (user_id);

CREATE TABLE IF NOT EXISTS trading_core.positions (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES trading_core.portfolios(id) ON DELETE CASCADE,
    symbol_ticker VARCHAR(16) NOT NULL,
    quantity NUMERIC(18,8) NOT NULL,
    entry_price NUMERIC(18,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_positions_portfolio_id
    ON trading_core.positions (portfolio_id);

CREATE INDEX IF NOT EXISTS idx_positions_symbol_ticker
    ON trading_core.positions (symbol_ticker);
