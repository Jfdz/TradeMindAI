"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo } from "react";

import { CandlestickChart } from "@/components/charts/CandlestickChart";
import { ArrowRightIcon } from "@/components/site/icons";
import { Button } from "@/components/ui/button";
import { type EnrichedHolding, type FilteredSignal } from "@/lib/dashboard/dashboard-api";
import { fetchDashboardPageData } from "@/lib/dashboard/client-data";
import { formatConfidence } from "@/lib/signal-utils";
import { cn } from "@/lib/utils";

const EMPTY_SIGNALS: FilteredSignal[] = [];
const EMPTY_HOLDINGS: EnrichedHolding[] = [];

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

function Sparkline({ values, color }: { values: number[]; color: string }) {
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

export default function DashboardHomePage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["dashboard"],
    queryFn: fetchDashboardPageData,
  });

  const portfolio = data?.portfolio ?? null;
  const signals = data?.signals ?? EMPTY_SIGNALS;
  const holdings = data?.holdings ?? EMPTY_HOLDINGS;
  const chartCandles = data?.chartCandles ?? [];
  const chartMarker = data?.chartMarker ?? null;
  const chartMarkers = useMemo(() => {
    return chartMarker ? [chartMarker] : undefined;
  }, [chartMarker]);

  const summaryCards = useMemo(() => {
    if (!portfolio) {
      return [];
    }

    const liveSignals = signals.filter((signal: FilteredSignal) => signal.live).length;

    return [
      { label: "Portfolio Value", value: formatMoney(portfolio.equity), detail: "Marked to market", tone: "text-green" },
      { label: "Open Positions", value: `${holdings.length}`, detail: "Backend portfolio book", tone: "text-white" },
      { label: "Live Signals", value: `${liveSignals}`, detail: `${signals.length} total signals`, tone: "text-cyan" },
      { label: "Unrealized P&L", value: formatSignedMoney(portfolio.unrealizedPnl), detail: "Open position gains", tone: "text-green" },
    ];
  }, [holdings.length, portfolio, signals]);

  const topSignal = signals[0] ?? null;

  if (isLoading) {
    return (
      <div className="space-y-8">
        <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="h-8 w-64 animate-pulse rounded-full bg-bg-2" />
          <div className="mt-4 h-14 w-full max-w-2xl animate-pulse rounded-2xl bg-bg-2" />
          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }, (_, index) => (
              <article key={index} className="h-32 animate-pulse rounded-[20px] bg-bg-2" />
            ))}
          </div>
        </section>
        <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
        </section>
      </div>
    );
  }

  if (error || !portfolio) {
    return (
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">Dashboard</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Unable to load dashboard
        </h2>
        <p className="mt-3 text-sm leading-7 text-text-2">
          {error instanceof Error ? error.message : "No portfolio records were returned."}
        </p>
      </section>
    );
  }

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Overview</div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              Good morning, Alex
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-text-2">
              You have {signals.length} signals in the backend feed, {holdings.length} open positions, and a live book
              that is now fully tied to the tradeMindAI data model.
            </p>
          </div>
          <div className="rounded-3xl border border-border bg-bg-2 px-5 py-4">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Market clock</div>
            <div className="mt-2 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
              {new Date().toLocaleDateString("en-US", {
                month: "short",
                day: "numeric",
                year: "numeric",
              })}
            </div>
            <div className="mt-1 text-sm text-text-2">Updated from backend and market data services</div>
          </div>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {summaryCards.map((card) => (
            <article key={card.label} className="rounded-[20px] border border-border bg-bg-2 p-5">
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{card.label}</div>
              <div className={cn("mt-3 font-display text-3xl font-bold tracking-[-0.05em]", card.tone)}>{card.value}</div>
              <div className="mt-2 text-sm text-text-2">{card.detail}</div>
            </article>
          ))}
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Live chart</div>
              <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
                {topSignal ? `${topSignal.symbol} ${topSignal.timeframe}` : "Portfolio market view"}
              </h3>
            </div>
            <Button asChild variant="outlineCyan" size="sm">
              <Link href="/dashboard/signals">
                View signals
                <ArrowRightIcon className="ml-2 h-4 w-4" />
              </Link>
            </Button>
          </div>

          <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
            {chartCandles.length > 0 ? (
              <CandlestickChart candles={chartCandles} markers={chartMarkers} showVolume={false} height={320} />
            ) : (
              <div className="flex h-[320px] items-center justify-center rounded-[18px] border border-dashed border-border text-sm text-text-2">
                No chart data available yet.
              </div>
            )}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Live signals</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Recent AI decisions</h3>

          <div className="mt-6 space-y-4">
            {signals.slice(0, 4).map((signal) => (
              <Link
                key={signal.id}
                href={`/dashboard/signals/${signal.id}`}
                className="block rounded-[20px] border border-border bg-bg-2 p-4 transition hover:border-border-strong hover:bg-bg-3"
              >
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="font-display text-lg font-semibold tracking-[-0.03em] text-white">{signal.symbol}</div>
                    <div className="mt-1 text-xs uppercase tracking-[0.22em] text-text-3">
                      {signal.timeframe} · {signal.age}
                    </div>
                  </div>
                  <div
                    className={cn(
                      "rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.22em]",
                      signal.type === "BUY"
                        ? "border-green/30 bg-[rgba(0,214,143,0.12)] text-green"
                        : signal.type === "SELL"
                          ? "border-red/30 bg-[rgba(255,77,106,0.12)] text-red"
                          : "border-gold/30 bg-[rgba(232,184,75,0.12)] text-gold"
                    )}
                  >
                    {signal.type}
                  </div>
                </div>
                <div className="mt-4 grid gap-3 text-sm text-text-2 sm:grid-cols-2">
                  <div>
                    <span className="text-text-3">Confidence</span>
                    <div className="mt-1 font-mono text-white">{formatConfidence(signal.confidence)}</div>
                  </div>
                  <div>
                    <span className="text-text-3">Reasoning</span>
                    <div className="mt-1 line-clamp-2 text-text-1">{signal.reasoning}</div>
                  </div>
                </div>
              </Link>
            ))}
            {signals.length === 0 ? (
              <div className="rounded-[20px] border border-dashed border-border bg-bg-2 px-4 py-6 text-sm text-text-2">
                No signals returned by the backend yet.
              </div>
            ) : null}
          </div>
        </article>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Open positions</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Portfolio exposure</h3>
          </div>
          <Button asChild variant="ghost" size="sm">
            <Link href="/dashboard/portfolio">Open portfolio</Link>
          </Button>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-[980px] w-full border-separate border-spacing-0">
            <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
              <tr>
                <th className="px-4 py-3 text-left">Asset</th>
                <th className="px-4 py-3 text-left">Qty</th>
                <th className="px-4 py-3 text-left">Avg cost</th>
                <th className="px-4 py-3 text-left">Current</th>
                <th className="px-4 py-3 text-left">P&amp;L</th>
                <th className="px-4 py-3 text-left">Sector</th>
                <th className="px-4 py-3 text-left">7d trend</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((position: EnrichedHolding, index: number) => {
                const pnl = position.unrealizedPnl;

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
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.lastPrice)}</td>
                    <td className={`border-t border-border px-4 py-4 font-mono ${pnl >= 0 ? "text-green" : "text-red"}`}>
                      {formatSignedMoney(pnl)}
                    </td>
                    <td className="border-t border-border px-4 py-4 text-text-2">{position.sector}</td>
                    <td className="border-t border-border px-4 py-4">
                      <Sparkline values={position.trend} color={position.color} />
                    </td>
                  </tr>
                );
              })}
              {holdings.length === 0 ? (
                <tr>
                  <td className="border-t border-border px-4 py-10 text-center text-sm text-text-2" colSpan={7}>
                    No open positions yet.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
