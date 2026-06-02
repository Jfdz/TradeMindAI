-- Per-signal performance tracking. 1:1 with trading_signals (signal_id is PK + FK).
-- Populated/updated daily by SignalPerformanceReviewRunner from close-of-day prices.
-- HOLD signals get no row (no target/stop thesis to resolve).
CREATE TABLE IF NOT EXISTS trading_core.signal_performance (
    signal_id      UUID         PRIMARY KEY
                       REFERENCES trading_core.trading_signals(id) ON DELETE CASCADE,
    ticker         TEXT         NOT NULL,
    generated_at   TIMESTAMPTZ  NOT NULL,
    entry_price    NUMERIC(18,6),
    price_1d       NUMERIC(18,6),
    price_3d       NUMERIC(18,6),
    price_7d       NUMERIC(18,6),
    price_30d      NUMERIC(18,6),
    max_profit     NUMERIC(8,4),  -- signed fraction since generation, e.g. 0.0480 = +4.8%
    max_drawdown   NUMERIC(8,4),  -- signed fraction, e.g. -0.0210 = -2.1%
    outcome        VARCHAR(8)   NOT NULL DEFAULT 'OPEN'
                       CHECK (outcome IN ('WIN', 'LOSS', 'OPEN')),
    resolved_at    TIMESTAMPTZ,   -- set when outcome leaves OPEN (first-touch bar date)
    evaluated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_signal_performance_outcome_generated
    ON trading_core.signal_performance (outcome, generated_at DESC);

-- rollback: DROP TABLE trading_core.signal_performance;
