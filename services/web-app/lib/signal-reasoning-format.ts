/**
 * Reasoning-text scrubber (EXECUTION_PLAN.md C2.2).
 *
 * The LLM is instructed to cite raw snake_case `price_facts` field names
 * (`sma_200`, `rsi_14`, `confidence`) and to quote grounded numbers
 * verbatim. That is correct for auditing but leaks jargon and untrimmed
 * decimals into user-facing copy. This module rewrites the reasoning
 * string for display only — the persisted artifact is untouched.
 *
 * Rules:
 *  1. Replace bare snake_case field tokens with a friendly label.
 *  2. A decimal number in a price context ("$", "USD", "price", "entry",
 *     "target", "stop", or right after a price-mapped label) → ceiling
 *     2 dp + " $" suffix (via {@link formatPriceUSD}).
 *  3. A decimal number in a confidence context ("confidence") → ceiling
 *     integer percent + " %" (via {@link formatConfidencePct}).
 *  4. RSI is a 0–100 indicator, not a percent: it stays raw, no suffix
 *     (EXECUTION_PLAN.md open-clarification #2, defaulted to raw).
 *  5. Anything else: the number is left verbatim — it is a grounded fact
 *     and we must not silently restate it.
 *
 * Rounding is ceiling everywhere ("redondear a la alza siempre", the
 * user's explicit instruction). Note the plan's C2.2 worked example
 * `"confidence 0.4321" → "43 %"` is inconsistent with ceiling
 * (ceil(43.21) = 44); the explicit ceiling rule wins and the test
 * pins `"44 %"`.
 */

import { formatConfidencePct, formatPriceUSD } from "@/lib/dashboard/format";
import type { SignalResponse } from "@/lib/api-client";

const FIELD_NAME_MAP: Record<string, string> = {
  sma_20: "20-day average",
  sma_50: "50-day average",
  sma_200: "200-day average",
  rsi_14: "RSI",
  macd_histogram: "momentum",
  pct_change_1d: "1-day change",
  pct_change_5d: "5-day change",
  pct_change_30d: "30-day change",
  volume_avg_20d: "average volume",
  volume: "volume",
  high_52w: "52-week high",
  low_52w: "52-week low",
  support: "support",
  resistance: "resistance",
  previous_close: "prior close",
  close: "price",
  entry_price: "entry",
  target_price: "target",
  stop_loss: "stop",
  expected_move_pct: "expected move",
  predicted_change_pct: "forecast",
  confidence: "confidence",
};

// Longest keys first so `sma_200` is replaced before a hypothetical `sma`,
// and `volume_avg_20d` before `volume`.
const FIELD_KEYS = Object.keys(FIELD_NAME_MAP).sort((a, b) => b.length - a.length);

const PRICE_CONTEXT = /(?:\$|usd|price|entry|target|stop|support|resistance|average|\bhigh\b|\blow\b|\bclose\b)/i;
const CONFIDENCE_CONTEXT = /confidence/i;
const RSI_CONTEXT = /\brsi\b/i;

/** A decimal or integer number, optionally signed. */
const NUMBER_RE = /-?\d+(?:\.\d+)?/g;

function replaceFieldNames(text: string): string {
  let out = text;
  for (const key of FIELD_KEYS) {
    // Word-boundary match on the snake_case token so substrings inside
    // other words are left alone.
    const re = new RegExp(`\\b${key}\\b`, "g");
    out = out.replace(re, FIELD_NAME_MAP[key]);
  }
  return out;
}

function reformatNumbers(text: string): string {
  return text.replace(NUMBER_RE, (match, offset: number) => {
    const value = Number(match);
    if (!Number.isFinite(value)) return match;

    // Look back a short window for the governing context word.
    const before = text.slice(Math.max(0, offset - 32), offset);

    // RSI wins over the generic price context and stays raw (rule 4).
    if (RSI_CONTEXT.test(before) && !PRICE_CONTEXT.test(before)) return match;

    if (CONFIDENCE_CONTEXT.test(before)) {
      return formatConfidencePct(value).replace(/^—$/, match);
    }
    if (PRICE_CONTEXT.test(before)) {
      return formatPriceUSD(value).replace(/^—$/, match);
    }
    return match;
  });
}

/**
 * Display-only rewrite of a signal's reasoning text. `signal` is accepted
 * for future per-signal disambiguation (e.g. clamping to the signal's own
 * grounded values); it is intentionally unused today so call sites stay
 * stable when that lands.
 */
export function scrubReasoningText(
  raw: string | null | undefined,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- reserved for per-signal grounding
  signal: SignalResponse,
): string {
  if (!raw) return "";
  return reformatNumbers(replaceFieldNames(raw));
}
