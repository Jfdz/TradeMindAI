"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  ApiError,
  apiClient,
  type PortfolioClosedPositionResponse,
  type PortfolioHoldingResponse,
} from "@/lib/api-client";
import type { EnrichedHolding } from "@/lib/dashboard/dashboard-api";
import { fetchPortfolioPageData } from "@/lib/dashboard/client-data";
import {
  buildClosePositionPayload,
  calculateClosePositionPnl,
  type ClosePositionDraft,
} from "@/lib/portfolio-close";

const EMPTY_HOLDINGS: EnrichedHolding[] = [];
const fieldCls =
  "w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none transition placeholder:text-text-3 focus:border-cyan/40";

function isMarketDataUnavailable(dataSource: string | null | undefined) {
  return dataSource === "unavailable";
}

function isPartialMarketData(dataSource: string | null | undefined) {
  return dataSource === "partial-market-data";
}

function isEmptyPortfolio(dataSource: string | null | undefined) {
  return dataSource === "none" || dataSource === "missing-portfolio";
}

type DataSourceState = "empty" | "unavailable" | "partial" | "ok";

function getDataSourceState(dataSource: string | null | undefined): DataSourceState {
  if (isEmptyPortfolio(dataSource)) return "empty";
  if (isMarketDataUnavailable(dataSource)) return "unavailable";
  if (isPartialMarketData(dataSource)) return "partial";
  return "ok";
}

function pickTotalValueDisplay(state: DataSourceState, totalCapital: number | null) {
  if (state === "empty") return formatMoney(0);
  if (totalCapital != null) return formatMoney(totalCapital);
  return "Unavailable";
}

function pickTotalValueDetail(state: DataSourceState, unpricedCount: number) {
  switch (state) {
    case "empty":
      return "No open positions";
    case "unavailable":
      return "Market data unavailable";
    case "partial":
      return `${unpricedCount} still unpriced`;
    default:
      return unpricedCount > 0 ? `${unpricedCount} unpriced` : "Marked to market";
  }
}

function pickUnrealizedValue(state: DataSourceState, unrealizedPnl: number | null) {
  if (state === "empty") return formatMoney(0);
  if (state === "unavailable") return "Unavailable";
  if (unrealizedPnl != null) return formatSignedMoney(unrealizedPnl);
  return "N/A";
}

function pickUnrealizedDetail(state: DataSourceState) {
  switch (state) {
    case "unavailable":
      return "Market data unavailable";
    case "partial":
      return "Priced holdings only";
    default:
      return "Open position gains";
  }
}

// ── presentation primitives ──────────────────────────────────────────────────
// Single source of truth for "given a numeric value, return a tone class".
// Zero is neutral: positive PnL is green, negative is red, otherwise white.
const TONE_NEUTRAL = "text-text-3";
const TONE_DEFAULT = "text-white";
const TONE_POSITIVE = "text-green";
const TONE_NEGATIVE = "text-red";

function signedTone(value: number | null | undefined, neutral = TONE_DEFAULT) {
  if (value == null) return TONE_NEUTRAL;
  if (value > 0) return TONE_POSITIVE;
  if (value < 0) return TONE_NEGATIVE;
  return neutral;
}

function pickUnrealizedTone(state: DataSourceState, unrealizedPnl: number | null) {
  if (state === "empty" || state === "unavailable") return TONE_NEUTRAL;
  return signedTone(unrealizedPnl, TONE_NEUTRAL);
}

function pickDonutCenterValue(state: DataSourceState, totalCapital: number | null) {
  if (totalCapital != null) return formatMoney(totalCapital);
  if (state === "empty") return formatMoney(0);
  return "Unavailable";
}

// `null/undefined → "—"` formatter wrapper. Lets callers compose any base
// formatter without re-implementing the null guard.
function orDash<T extends number>(value: T | null | undefined, fmt: (v: T) => string) {
  return value == null ? "—" : fmt(value);
}

function formatMoneyOrDash(value: number | null | undefined) {
  return orDash(value, formatMoney);
}

function formatSignedMoneyOrDash(value: number | null | undefined) {
  return orDash(value, formatSignedMoney);
}

function formatPercentOrDash(value: number | null | undefined, digits = 1) {
  return orDash(value, (v) => `${v.toFixed(digits)}%`);
}

function formatWinRate(winRate: number | null | undefined) {
  if (winRate == null) return "N/A";
  return `${Math.round(winRate * 100)}%`;
}

function calculatePnlPct(pnl: number | null, costBasis: number) {
  if (pnl == null || costBasis <= 0) return null;
  return (pnl / costBasis) * 100;
}

function formatSignedPercent(value: number, digits = 2) {
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(digits)}%`;
}

function formatPnlPctCell(lastPrice: number | null | undefined, pnlPct: number | null) {
  if (lastPrice == null) return "—";
  return formatSignedPercent(pnlPct ?? 0);
}

function formatMoney(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

// Zero renders as plain `$0.00` (no sign). Positive gets `+`, negative gets `-`.
// Callers that want a forced sign on zero must format manually.
function formatSignedMoney(value: number) {
  const formatted = formatMoney(Math.abs(value));
  if (value > 0) return `+${formatted}`;
  if (value < 0) return `-${formatted}`;
  return formatted;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return "—";
  }

  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function getDonutTextClass(value: number) {
  const length = formatMoney(value).length;
  if (length <= 8) return "mt-3 font-display text-3xl font-bold tracking-[-0.05em] text-white";
  if (length <= 11) return "mt-3 font-display text-2xl font-bold tracking-[-0.05em] text-white";
  return "mt-3 font-display text-xl font-bold tracking-[-0.05em] text-white";
}

function Sparkline({ values, color }: { values: number[]; color: string }) {
  if (values.length === 0) {
    return <span className="text-text-3 text-xs">—</span>;
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const width = 120;
  const height = 36;

  const points = values
    .map((value, index) => {
      const x = (index / Math.max(values.length - 1, 1)) * width;
      const normalized = max === min ? 0.5 : (value - min) / (max - min);
      const y = height - normalized * height;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <svg className="h-9 w-32" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
      <polyline fill="none" points={points} stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function ClosePositionPanel({
  position,
  onClosed,
  onAlreadyClosed,
  onClose,
}: {
  position: PortfolioHoldingResponse;
  onClosed: (payload: { realizedPnl: number }) => void;
  onAlreadyClosed: () => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<ClosePositionDraft>({
    exitPrice: "",
    fees: "",
    closedAt: "",
  });
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const firstRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    firstRef.current?.focus();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      if (!position.id) {
        toast.error("Cannot close position: missing position ID. Contact support.");
        return;
      }
      const payload = buildClosePositionPayload(draft);
      setSubmitting(true);
      setErr(null);
      await apiClient.closePosition(position.id, payload);
      onClosed({ realizedPnl: calculateClosePositionPnl(position, payload) });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        onAlreadyClosed();
        return;
      }
      setErr(error instanceof Error ? error.message : "Failed to close position");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-end sm:items-center sm:justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-md rounded-t-[28px] border border-border bg-bg-1 p-6 shadow-glow sm:rounded-[28px]">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Close Position</div>
        <h3 className="mt-2 font-display text-2xl font-bold tracking-[-0.04em] text-white">{position.symbol}</h3>
        <p className="mt-2 text-sm text-text-2">Record the final sale price, optional fees, and close time for this full exit.</p>
        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Exit Price (USD)</span>
            <input
              ref={firstRef}
              type="number"
              min="0.01"
              step="any"
              className={fieldCls}
              placeholder="185.00"
              value={draft.exitPrice}
              onChange={(e) => setDraft((current) => ({ ...current, exitPrice: e.target.value }))}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Fees (USD)</span>
            <input
              type="number"
              min="0"
              step="any"
              className={fieldCls}
              placeholder="0.00"
              value={draft.fees}
              onChange={(e) => setDraft((current) => ({ ...current, fees: e.target.value }))}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Closed At</span>
            <input
              type="datetime-local"
              className={fieldCls}
              value={draft.closedAt}
              onChange={(e) => setDraft((current) => ({ ...current, closedAt: e.target.value }))}
            />
          </label>
          {err ? <p className="text-sm text-red">{err}</p> : null}
          <div className="flex gap-3 pt-2">
            <Button type="submit" variant="cyan" disabled={submitting} className="flex-1">
              {submitting ? "Closing..." : "Close Position"}
            </Button>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function PortfolioPage() {
  const queryClient = useQueryClient();
  const [positionToClose, setPositionToClose] = useState<PortfolioHoldingResponse | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ["portfolio"],
    queryFn: fetchPortfolioPageData,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    refetchOnMount: "always",
  });

  const portfolio = data?.portfolio ?? null;
  const holdings = data?.holdings ?? EMPTY_HOLDINGS;
  const closedPositions = portfolio?.closedPositions ?? [];

  const summary = useMemo(() => {
    if (!portfolio) {
      return null;
    }

    const totalCost = holdings.reduce((sum, h) => sum + h.quantity * h.averageCost, 0);
    const unpricedCount = holdings.filter((h) => h.lastPrice == null).length;
    const state = getDataSourceState(portfolio.dataSource);

    const showCostZero = state === "empty" || totalCost > 0;
    const costBasisValue = showCostZero ? formatMoney(totalCost) : "—";
    const winRateValue = formatWinRate(portfolio.winRate);

    return [
      {
        label: "Total Value",
        value: pickTotalValueDisplay(state, portfolio.totalCapital),
        detail: pickTotalValueDetail(state, unpricedCount),
      },
      {
        label: "Total Cost Basis",
        value: costBasisValue,
        detail: "Weighted entry cost",
      },
      {
        label: "Unrealized P&L",
        value: pickUnrealizedValue(state, portfolio.unrealizedPnl),
        detail: pickUnrealizedDetail(state),
        tone: pickUnrealizedTone(state, portfolio.unrealizedPnl),
      },
      {
        label: "Win Rate",
        value: winRateValue,
        detail: "Position-level",
      },
    ];
  }, [portfolio, holdings]);

  const allocationGradient = useMemo(() => {
    if (!holdings.length) {
      return "rgba(0,200,212,0.35) 0% 100%";
    }

    const hasAllocation = holdings.some((h) => h.allocationPct != null);
    if (!hasAllocation) {
      return "rgba(100,100,100,0.3) 0% 100%";
    }

    let start = 0;
    return holdings
      .map((holding) => {
        const pct = holding.allocationPct ?? 0;
        const end = start + pct;
        const segment = `${holding.color} ${start}% ${end}%`;
        start = end;
        return segment;
      })
      .join(", ");
  }, [holdings]);

  if (isLoading) {
    return (
      <div className="space-y-8">
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <article key={index} className="h-32 animate-pulse rounded-[20px] bg-bg-1/80" />
          ))}
        </section>
        <section className="grid gap-6 lg:grid-cols-[300px_1fr]">
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
        </section>
      </div>
    );
  }

  if (error || !portfolio || summary == null) {
    return (
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">Portfolio</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Unable to load portfolio
        </h2>
        <p className="mt-3 text-sm leading-7 text-text-2">
          {error instanceof Error ? error.message : "No portfolio records were returned."}
        </p>
      </section>
    );
  }

  return (
    <div className="space-y-8">
      {positionToClose ? (
        <ClosePositionPanel
          position={positionToClose}
          onClosed={({ realizedPnl }) => {
            const symbol = positionToClose.symbol;
            setPositionToClose(null);
            void queryClient.invalidateQueries({ queryKey: ["portfolio"] });
            toast.success(`${symbol} closed with ${formatSignedMoney(realizedPnl)} realized`);
          }}
          onAlreadyClosed={() => {
            const symbol = positionToClose.symbol;
            setPositionToClose(null);
            void queryClient.invalidateQueries({ queryKey: ["portfolio"] });
            toast.message(`${symbol} was already closed — refreshed your portfolio`);
          }}
          onClose={() => setPositionToClose(null)}
        />
      ) : null}

      {isMarketDataUnavailable(portfolio.dataSource) ? (
        <section className="rounded-[20px] border border-gold/30 bg-[rgba(232,184,75,0.12)] px-5 py-4 text-sm text-gold">
          Market data is unavailable. Open positions are shown without current prices until the pricing service recovers.
        </section>
      ) : isPartialMarketData(portfolio.dataSource) ? (
        <section className="rounded-[20px] border border-gold/30 bg-[rgba(232,184,75,0.12)] px-5 py-4 text-sm text-gold">
          Market data returned only a partial price set. Holdings without a live quote remain visible as unpriced until the next refresh.
        </section>
      ) : null}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summary.map((card) => (
          <article key={card.label} className="rounded-[20px] border border-border bg-bg-1/80 p-5 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{card.label}</div>
            <div className={`mt-3 font-display text-3xl font-bold tracking-[-0.05em] ${"tone" in card ? card.tone : "text-white"}`}>{card.value}</div>
            <div className="mt-2 text-sm text-text-2">{card.detail}</div>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[300px_1fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Portfolio Mix</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Allocation donut</h3>

          <div className="mt-8 flex items-center justify-center">
            <div className="relative h-56 w-56 rounded-full" style={{ background: `conic-gradient(${allocationGradient})` }}>
              <div className="absolute inset-4 rounded-full border border-border bg-bg-0/95 p-3 text-center">
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Portfolio</div>
                <div className={getDonutTextClass(portfolio.totalCapital ?? 0)}>
                  {pickDonutCenterValue(getDataSourceState(portfolio.dataSource), portfolio.totalCapital)}
                </div>
                <div className={`mt-2 whitespace-nowrap text-sm ${signedTone(portfolio.realizedPnl)}`}>
                  Realized {formatSignedMoney(portfolio.realizedPnl)}
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {holdings.map((position) => (
              <div key={position.id} className="flex items-center justify-between rounded-2xl border border-border bg-bg-2 px-4 py-3">
                <div className="flex items-center gap-3">
                  <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                  <div>
                    <div className="text-sm font-semibold text-white">{position.symbol}</div>
                    <div className="text-xs text-text-3">{position.sector}</div>
                  </div>
                </div>
                <div className="font-mono text-sm text-text-1">
                  {formatPercentOrDash(position.allocationPct)}
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Positions</div>
              <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Open holdings</h3>
            </div>
            <Link
              href="/dashboard/portfolio/add"
              className="inline-flex items-center gap-2 rounded-full bg-cyan px-4 py-2 text-xs font-semibold uppercase tracking-[0.15em] text-black transition-opacity hover:opacity-80"
            >
              + Add Position
            </Link>
          </div>

          <div className="mt-6 overflow-x-auto">
            {holdings.length === 0 ? (
              <div className="py-12 text-center text-sm text-text-3">
                No open positions. Use &ldquo;Add Position&rdquo; to track your holdings.
              </div>
            ) : (
              <table className="min-w-[1080px] w-full border-separate border-spacing-0">
                <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                  <tr>
                    <th className="px-4 py-3 text-left">Asset</th>
                    <th className="px-4 py-3 text-left">Qty</th>
                    <th className="px-4 py-3 text-left">Avg cost</th>
                    <th className="px-4 py-3 text-left">Current</th>
                    <th className="px-4 py-3 text-left">P&amp;L</th>
                    <th className="px-4 py-3 text-left">P&amp;L %</th>
                    <th className="px-4 py-3 text-left">7d sparkline</th>
                    <th className="px-4 py-3 text-left">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map((position, index) => {
                    const pnl = position.unrealizedPnl ?? null;
                    const costBasis = position.quantity * position.averageCost;
                    const pnlPct = calculatePnlPct(pnl, costBasis);
                    const rowClass = index % 2 === 0 ? "bg-white/[0.015]" : "";
                    const lastPriceCell = formatMoneyOrDash(position.lastPrice);
                    const pnlCell = formatSignedMoneyOrDash(pnl);
                    const pnlPctCell = formatPnlPctCell(position.lastPrice, pnlPct);

                    return (
                      <tr key={position.id} className={rowClass}>
                        <td className="border-t border-border px-4 py-4">
                          <div className="flex items-center gap-3">
                            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                            <div>
                              <div className="font-semibold text-white">{position.symbol}</div>
                              <div className="text-xs text-text-3">{position.name}</div>
                            </div>
                          </div>
                        </td>
                        <td className="border-t border-border px-4 py-4 font-mono text-text-1">{position.quantity}</td>
                        <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.averageCost)}</td>
                        <td className="border-t border-border px-4 py-4 font-mono text-text-1">{lastPriceCell}</td>
                        <td className={`border-t border-border px-4 py-4 font-mono ${signedTone(pnl, TONE_NEUTRAL)}`}>
                          {pnlCell}
                        </td>
                        <td className={`border-t border-border px-4 py-4 font-mono ${signedTone(pnlPct, TONE_NEUTRAL)}`}>
                          {pnlPctCell}
                        </td>
                        <td className="border-t border-border px-4 py-4">
                          <Sparkline values={position.trend} color={position.color} />
                        </td>
                        <td className="border-t border-border px-4 py-4">
                          <button
                            type="button"
                            onClick={() => setPositionToClose(position)}
                            className="rounded-full border border-cyan/30 px-3 py-1 text-[11px] uppercase tracking-[0.18em] text-cyan transition hover:bg-cyan/10"
                          >
                            Close
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </article>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">History</div>
        <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Closed positions</h3>
        <p className="mt-2 text-sm text-text-2">Realized exits stay visible here after they leave the open holdings table.</p>

        <div className="mt-6 overflow-x-auto">
          {closedPositions.length === 0 ? (
            <div className="py-12 text-center text-sm text-text-3">No closed positions recorded yet.</div>
          ) : (
            <table className="min-w-[1080px] w-full border-separate border-spacing-0">
              <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                <tr>
                  <th className="px-4 py-3 text-left">Asset</th>
                  <th className="px-4 py-3 text-left">Qty</th>
                  <th className="px-4 py-3 text-left">Avg cost</th>
                  <th className="px-4 py-3 text-left">Exit</th>
                  <th className="px-4 py-3 text-left">Fees</th>
                  <th className="px-4 py-3 text-left">Realized P&amp;L</th>
                  <th className="px-4 py-3 text-left">Opened</th>
                  <th className="px-4 py-3 text-left">Closed</th>
                </tr>
              </thead>
              <tbody>
                {closedPositions.map((position: PortfolioClosedPositionResponse, index: number) => (
                  <tr key={position.id} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
                    <td className="border-t border-border px-4 py-4 font-semibold text-white">{position.symbol}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{position.quantity}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.averageCost)}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.exitPrice)}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.fees)}</td>
                    <td className={`border-t border-border px-4 py-4 font-mono ${signedTone(position.realizedPnl)}`}>
                      {formatSignedMoney(position.realizedPnl)}
                    </td>
                    <td className="border-t border-border px-4 py-4 text-sm text-text-2">{formatDateTime(position.openedAt)}</td>
                    <td className="border-t border-border px-4 py-4 text-sm text-text-2">{formatDateTime(position.closedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>
    </div>
  );
}
