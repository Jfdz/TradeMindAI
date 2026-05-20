/**
 * Canonical formatters and tone helpers for the dashboard surfaces.
 *
 * Importing from here is preferred over inlining `formatMoney`/ternary tone
 * logic in pages, so currency, percent, and color rules stay consistent.
 *
 * Conventions:
 * - `formatSignedMoney(0)` returns `$0.00` (no sign). Only positive/negative
 *   values get `+`/`-`. Same for `formatSignedPercent`.
 * - Tones use the design-system semantic classes (`text-green` / `text-red`
 *   / `text-white` / `text-text-3`) — never raw hex.
 * - Null/undefined values render as `—` via `orDash`.
 */

export const TONE_NEUTRAL = "text-text-3";
export const TONE_DEFAULT = "text-white";
export const TONE_POSITIVE = "text-green";
export const TONE_NEGATIVE = "text-red";

export type Tone = typeof TONE_NEUTRAL | typeof TONE_DEFAULT | typeof TONE_POSITIVE | typeof TONE_NEGATIVE;

/**
 * Returns the tone class for a signed numeric value.
 *
 * - `null` / `undefined` → {@link TONE_NEUTRAL}
 * - `> 0` → {@link TONE_POSITIVE}
 * - `< 0` → {@link TONE_NEGATIVE}
 * - `0`   → `neutral` (defaults to {@link TONE_DEFAULT})
 *
 * @param neutral tone for the exact-zero case. Pass {@link TONE_NEUTRAL} for
 * surfaces where zero should look the same as "no data".
 */
export function signedTone(value: number | null | undefined, neutral: Tone = TONE_DEFAULT): Tone {
  if (value == null) return TONE_NEUTRAL;
  if (value > 0) return TONE_POSITIVE;
  if (value < 0) return TONE_NEGATIVE;
  return neutral;
}

export function formatMoney(value: number): string {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

/**
 * Currency with explicit sign for non-zero values. Zero is rendered without a
 * sign (`$0.00`), since "+$0.00" reads as a fake gain on UI surfaces.
 */
export function formatSignedMoney(value: number): string {
  const formatted = formatMoney(Math.abs(value));
  if (value > 0) return `+${formatted}`;
  if (value < 0) return `-${formatted}`;
  return formatted;
}

/** Percentage with explicit `+` for positive values; zero and negatives are unsigned. */
export function formatSignedPercent(value: number, digits = 2): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(digits)}%`;
}

/**
 * Wraps any base formatter with a `null/undefined → "—"` guard so call sites
 * don't have to repeat the same null check.
 */
export function orDash<T extends number>(value: T | null | undefined, fmt: (v: T) => string): string {
  return value == null ? "—" : fmt(value);
}

export function formatMoneyOrDash(value: number | null | undefined): string {
  return orDash(value, formatMoney);
}

export function formatSignedMoneyOrDash(value: number | null | undefined): string {
  return orDash(value, formatSignedMoney);
}

export function formatPercentOrDash(value: number | null | undefined, digits = 1): string {
  return orDash(value, (v) => `${v.toFixed(digits)}%`);
}

/**
 * Spec-driven price formatter (C2.1): ceiling to 2 dp, suffix `$`.
 *
 * Differs from {@link formatMoney} (which prefixes `$140.42`). Used in the
 * signal-detail / rationale contexts where the product spec asks for the
 * trailing-symbol form `140.42 $`. Rounding is ceiling ("redondear a la
 * alza siempre") — see EXECUTION_PLAN.md open-clarification #1.
 */
export function formatPriceUSD(value: number | null | undefined): string {
  if (value == null) return "—";
  const cents = Math.ceil(value * 100) / 100;
  return `${cents.toFixed(2)} $`;
}

/** Confidence as a ceiling integer percent with suffix, e.g. `43 %`. */
export function formatConfidencePct(value: number | null | undefined): string {
  if (value == null) return "—";
  const pct = Math.ceil(value * 100);
  return `${pct} %`;
}
