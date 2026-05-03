"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";

import { apiClient } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import type { EnrichedHolding } from "@/lib/dashboard/dashboard-api";
import { fetchPortfolioPageData } from "@/lib/dashboard/client-data";

const EMPTY_HOLDINGS: EnrichedHolding[] = [];

function isMarketDataUnavailable(dataSource: string | null | undefined) {
  return dataSource === "unavailable";
}

function isPartialMarketData(dataSource: string | null | undefined) {
  return dataSource === "partial-market-data";
}

function formatMoney(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

function formatSignedMoney(value: number) {
  const formatted = formatMoney(Math.abs(value));
  return value >= 0 ? `+${formatted}` : `-${formatted}`;
}

function getDonutTextClass(value: number) {
  const length = formatMoney(value).length;
  if (length <= 8) return "mt-3 font-display text-3xl font-bold tracking-[-0.05em] text-white";
  if (length <= 11) return "mt-3 font-display text-2xl font-bold tracking-[-0.05em] text-white";
  return "mt-3 font-display text-xl font-bold tracking-[-0.05em] text-white";
}

function Sparkline({ values, color }: { values: number[]; color: string }) {
  if (values.length === 0) {
    return null;
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

const fieldCls =
  "w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none transition placeholder:text-text-3 focus:border-cyan/40";

function AddPositionPanel({
  onAdded,
  onClose,
}: {
  onAdded: () => void;
  onClose: () => void;
}) {
  const [ticker, setTicker] = useState("");
  const [quantity, setQuantity] = useState("");
  const [entryPrice, setEntryPrice] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const firstRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    firstRef.current?.focus();
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const qty = parseFloat(quantity);
    const price = parseFloat(entryPrice);
    if (!ticker.trim() || isNaN(qty) || qty <= 0 || isNaN(price) || price <= 0) {
      setErr("Please fill all fields with valid positive numbers.");
      return;
    }
    setSubmitting(true);
    setErr(null);
    try {
      await apiClient.addPosition({ ticker: ticker.trim().toUpperCase(), quantity: qty, entryPrice: price });
      toast.success(`${ticker.toUpperCase()} position added`);
      onAdded();
    } catch (error) {
      setErr(error instanceof Error ? error.message : "Failed to add position");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-end sm:items-center sm:justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-md rounded-t-[28px] sm:rounded-[28px] border border-border bg-bg-1 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Portfolio</div>
        <h3 className="mt-2 font-display text-2xl font-bold tracking-[-0.04em] text-white">Add position</h3>
        <p className="mt-2 text-sm text-text-2">Enter the ticker, quantity and your average entry price.</p>
        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Ticker</span>
            <input
              ref={firstRef}
              className={fieldCls}
              placeholder="e.g. AAPL"
              value={ticker}
              onChange={(e) => setTicker(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Quantity</span>
            <input
              type="number"
              min="0.00000001"
              step="any"
              className={fieldCls}
              placeholder="10"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </label>
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Entry price (USD)</span>
            <input
              type="number"
              min="0.01"
              step="any"
              className={fieldCls}
              placeholder="178.50"
              value={entryPrice}
              onChange={(e) => setEntryPrice(e.target.value)}
            />
          </label>
          {err ? <p className="text-sm text-red">{err}</p> : null}
          <div className="flex gap-3 pt-2">
            <Button type="submit" variant="cyan" disabled={submitting} className="flex-1">
              {submitting ? "Adding…" : "Add position"}
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
  const [showAddForm, setShowAddForm] = useState(false);
  const { data, isLoading, error } = useQuery({
    queryKey: ["portfolio"],
    queryFn: fetchPortfolioPageData,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });
  const portfolio = data?.portfolio ?? null;
  const holdings = data?.holdings ?? EMPTY_HOLDINGS;
  const totalValue = holdings.reduce((sum, h) => sum + (h.marketValue ?? 0), 0);

  const summary = useMemo(() => {
    if (!portfolio) {
      return null;
    }

    const totalCost = holdings.reduce((sum, h) => sum + (h.quantity * h.averageCost), 0);
    const unpricedCount = holdings.filter((h) => h.lastPrice == null).length;
    const marketDataUnavailable = isMarketDataUnavailable(portfolio.dataSource);
    const partialMarketData = isPartialMarketData(portfolio.dataSource);

    const unrealizedPnl = holdings.reduce(
      (sum, h) => sum + (h.unrealizedPnl ?? 0),
      0
    );

    return [
      {
        label: "Total Value",
        value: totalValue > 0 ? formatMoney(totalValue) : "—",
        detail: marketDataUnavailable
          ? "Market data unavailable"
          : partialMarketData
            ? `${unpricedCount} still unpriced`
          : unpricedCount > 0
            ? `${unpricedCount} unpriced`
            : "Marked to market",
      },
      {
        label: "Total Cost Basis",
        value: totalCost > 0 ? formatMoney(totalCost) : "—",
        detail: "Weighted entry cost",
      },
      {
        label: "Unrealized P&L",
        value: marketDataUnavailable ? "N/A" : formatSignedMoney(unrealizedPnl),
        detail: marketDataUnavailable ? "Market data unavailable" : partialMarketData ? "Priced holdings only" : "Open position gains",
      },
      {
        label: "Win Rate",
        value: `${Math.round(portfolio.winRate * 100)}%`,
        detail: "Position-level",
      },
    ];
  }, [portfolio, holdings, totalValue]);

  const allocationGradient = useMemo(() => {
    if (!holdings.length) {
      return "rgba(0,200,212,0.35) 0% 100%";
    }

    let start = 0;
    return holdings
      .map((holding) => {
        const end = start + holding.allocationPct;
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
      {showAddForm ? (
        <AddPositionPanel
          onAdded={() => {
            setShowAddForm(false);
            queryClient.invalidateQueries({ queryKey: ["portfolio"] });
          }}
          onClose={() => setShowAddForm(false)}
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
            <div className="mt-3 font-display text-3xl font-bold tracking-[-0.05em] text-white">{card.value}</div>
            <div className="mt-2 text-sm text-text-2">{card.detail}</div>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[300px_1fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Portfolio mix</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Allocation donut</h3>

          <div className="mt-8 flex items-center justify-center">
            <div className="relative h-56 w-56 rounded-full" style={{ background: `conic-gradient(${allocationGradient})` }}>
              <div className="absolute inset-4 rounded-full border border-border bg-bg-0/95 p-3 text-center">
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Portfolio</div>
                <div className={getDonutTextClass(totalValue)}>
                  {totalValue > 0 ? formatMoney(totalValue) : "—"}
                </div>
                <div className="mt-2 text-sm text-text-2 whitespace-nowrap">
                  Realized {formatSignedMoney(portfolio.realizedPnl)}
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {holdings.map((position) => (
              <div key={position.symbol} className="flex items-center justify-between rounded-2xl border border-border bg-bg-2 px-4 py-3">
                <div className="flex items-center gap-3">
                  <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                  <div>
                    <div className="text-sm font-semibold text-white">{position.symbol}</div>
                    <div className="text-xs text-text-3">{position.sector}</div>
                  </div>
                </div>
                <div className="font-mono text-sm text-text-1">{position.allocationPct.toFixed(1)}%</div>
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
            {holdings.length === 0 && (
              <div className="py-12 text-center text-sm text-text-3">
                No open positions. Use &ldquo;Add Position&rdquo; to track your holdings.
              </div>
            )}
            {holdings.length > 0 && (
            <table className="min-w-[980px] w-full border-separate border-spacing-0">
              <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                <tr>
                  <th className="px-4 py-3 text-left">Asset</th>
                  <th className="px-4 py-3 text-left">Qty</th>
                  <th className="px-4 py-3 text-left">Avg cost</th>
                  <th className="px-4 py-3 text-left">Current</th>
                  <th className="px-4 py-3 text-left">P&amp;L</th>
                  <th className="px-4 py-3 text-left">P&amp;L %</th>
                  <th className="px-4 py-3 text-left">7d sparkline</th>
                </tr>
              </thead>
              <tbody>
                {holdings.map((position, index) => {
                  const pnl = position.unrealizedPnl ?? null;
                  const costBasis = position.quantity * position.averageCost;
                  const pnlPct = costBasis > 0 && pnl != null ? (pnl / costBasis) * 100 : null;

                  return (
                    <tr key={position.symbol} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
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
                      <td className="border-t border-border px-4 py-4 font-mono text-text-1">
                        {position.lastPrice != null ? formatMoney(position.lastPrice) : "—"}
                      </td>
                      <td className={`border-t border-border px-4 py-4 font-mono ${pnl != null && pnl >= 0 ? "text-green" : pnl != null && pnl < 0 ? "text-red" : "text-text-3"}`}>
                        {pnl != null ? formatSignedMoney(pnl) : "—"}
                      </td>
                      <td className={`border-t border-border px-4 py-4 font-mono ${pnlPct != null && pnlPct >= 0 ? "text-green" : pnlPct != null && pnlPct < 0 ? "text-red" : "text-text-3"}`}>
                        {position.lastPrice != null ? (pnlPct != null && pnlPct >= 0 ? "+" : "") + (pnlPct?.toFixed(2) ?? "0.00") + "%" : "—"}
                      </td>
                      <td className="border-t border-border px-4 py-4">
                        <Sparkline values={position.trend} color={position.color} />
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
    </div>
  );
}
