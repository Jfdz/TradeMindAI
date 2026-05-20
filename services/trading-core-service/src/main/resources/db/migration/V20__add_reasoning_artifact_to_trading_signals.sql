-- C6 (TS-318) — Reasoning artifact persistence.
--
-- Extends trading_signals with the C5 reasoning pipeline outcomes so the
-- admin audit endpoint can replay why a reasoning was generated, refused,
-- or validated-out.
--
-- `reasoning`, `reasoning_status`, `reasoning_generated_at` from V16 stay
-- intact and continue to drive the user-facing UI. The new columns below
-- are append-only audit detail.

ALTER TABLE trading_core.trading_signals
    -- Fine-grained ai-engine outcome enum
    -- (GENERATED, REFUSED_BY_LLM, REFUSED_BY_VALIDATOR, REFUSED_LLM_DISABLED,
    --  REFUSED_NO_FACTS, ERROR). VARCHAR(50) gives headroom over the
    -- existing reasoning_status VARCHAR(16) which is too short.
    ADD COLUMN IF NOT EXISTS reasoning_outcome              VARCHAR(50),

    -- Which LLM provider produced this reasoning.
    -- ("stub" | "anthropic_oauth" | "anthropic_api_key")
    ADD COLUMN IF NOT EXISTS reasoning_provider             VARCHAR(50),

    -- Model identifier as reported by the SDK call (e.g. "claude-haiku-4-5").
    ADD COLUMN IF NOT EXISTS reasoning_model_version        VARCHAR(100),

    -- How many retries the C5 validator triggered (0 or 1).
    ADD COLUMN IF NOT EXISTS reasoning_retry_count          INTEGER NOT NULL DEFAULT 0,

    -- Human-readable refusal reason when outcome is one of the REFUSED_* values.
    ADD COLUMN IF NOT EXISTS reasoning_refusal_reason       TEXT,

    -- Full ReasoningContext (price_facts + news + signal) at generation time.
    -- This is the audit anchor: every number/event in `reasoning` must trace
    -- back into this blob (the C5 validator enforced this before persistence).
    ADD COLUMN IF NOT EXISTS reasoning_facts_snapshot       JSONB,

    -- price_facts field names cited in `reasoning` (e.g. ["sma_200","rsi_14"]).
    ADD COLUMN IF NOT EXISTS reasoning_price_refs           JSONB,

    -- News URLs cited in `reasoning`. Each URL must appear in
    -- reasoning_facts_snapshot.news[].url.
    ADD COLUMN IF NOT EXISTS reasoning_news_refs            JSONB,

    -- Populated only when outcome=REFUSED_BY_VALIDATOR; each entry is
    -- {type, detail} so we can build a histogram of which rules trip.
    ADD COLUMN IF NOT EXISTS reasoning_validator_violations JSONB,

    -- Raw provider response audit (model, stop_reason, usage tokens, etc.).
    -- Opaque to schema; useful for cost analysis and post-mortem.
    ADD COLUMN IF NOT EXISTS reasoning_raw_audit            JSONB;

-- Partial index supporting the "show me recent failures" admin query
-- without bloating reads on the common GENERATED case.
CREATE INDEX IF NOT EXISTS idx_trading_signals_reasoning_outcome_failures
    ON trading_core.trading_signals (reasoning_outcome, generated_at DESC)
    WHERE reasoning_outcome IS NOT NULL AND reasoning_outcome <> 'GENERATED';
