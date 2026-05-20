-- Deduplicate trading_signals and enforce per-day uniqueness keyed on entry_price.
-- Two signals on the same UTC day for the same (ticker, signal_type, timeframe) are
-- treated as duplicates ONLY if their entry_price is identical (NULL collides
-- with NULL via COALESCE). Different entry prices on the same day are allowed.
--
-- We cannot use date_trunc('day', generated_at) in the unique index because
-- date_trunc on timestamptz is STABLE (timezone-dependent), and PostgreSQL
-- requires expressions in unique indexes to be IMMUTABLE. The expression
-- (generated_at AT TIME ZONE 'UTC')::date IS immutable because the timezone
-- literal is a constant.

-- Step 1: collapse existing duplicates, keeping the most recent per group.
WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY ticker, signal_type, timeframe,
                        (generated_at AT TIME ZONE 'UTC')::date,
                        COALESCE(entry_price, -1)
           ORDER BY generated_at DESC
         ) AS rn
  FROM trading_core.trading_signals
)
DELETE FROM trading_core.trading_signals
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- Step 2: enforce uniqueness going forward.
-- COALESCE wraps entry_price so NULL collides with NULL.
CREATE UNIQUE INDEX IF NOT EXISTS trading_signals_unique_per_day_entry
  ON trading_core.trading_signals (
    ticker,
    signal_type,
    timeframe,
    ((generated_at AT TIME ZONE 'UTC')::date),
    (COALESCE(entry_price, -1))
  );
