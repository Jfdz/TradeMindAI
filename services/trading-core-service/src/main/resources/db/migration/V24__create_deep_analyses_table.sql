-- Fase 3 — premium multi-agent deep-analysis artifacts.
--
-- Persists the grounded bull/bear/judge/risk debate that ai-engine computes
-- on demand for a signal. Deep analysis is non-deterministic and expensive, so
-- (unlike the per-signal grounded reasoning on trading_signals) it is stored in
-- its own table, generated only when a PREMIUM user requests it, and kept as a
-- single current row per signal (regenerate replaces via the unique key).
--
-- The full multi-section artifact (sections + verdict + refs) lives in the
-- `artifact` JSONB. Three scalars are promoted as columns for cheap querying
-- and UI badges: `outcome` (GENERATED|PARTIAL), `verdict_direction`
-- (BULLISH|BEARISH|NEUTRAL) and `conviction` (AGREES|CONTRADICTS|UNCERTAIN —
-- CONTRADICTS is the soft low-conviction review flag).

CREATE TABLE IF NOT EXISTS trading_core.deep_analyses (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id         UUID         NOT NULL
        REFERENCES trading_core.trading_signals (id) ON DELETE CASCADE,
    outcome           VARCHAR(20)  NOT NULL,
    verdict_direction VARCHAR(10)  NOT NULL,
    conviction        VARCHAR(12)  NOT NULL,
    artifact          JSONB        NOT NULL,
    generated_at      TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_deep_analyses_signal_id UNIQUE (signal_id)
);

-- rollback: DROP TABLE trading_core.deep_analyses;
