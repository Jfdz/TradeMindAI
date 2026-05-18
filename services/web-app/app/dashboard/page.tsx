"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useUser } from "@clerk/nextjs";
import { useCallback, useMemo, useState } from "react";

import { CandlestickChart } from "@/components/charts/CandlestickChart";
import { AiDecisionCard } from "@/components/dashboard/ai-decision-card";
import { LiveSignalsStrip } from "@/components/dashboard/live-signals-strip";
import { ArrowRightIcon } from "@/components/site/icons";
import { Button } from "@/components/ui/button";
import { LiveLed } from "@/components/ui/live-led";
import type { DashboardCandle, EnrichedHolding, FilteredSignal } from "@/lib/dashboard/dashboard-api";
import { fetchDashboardPageData } from "@/lib/dashboard/client-data";
import { useAgeOutToast } from "@/lib/dashboard/use-age-out-toast";
import { useStockLogos } from "@/lib/dashboard/use-stock-logos";
import { buildSignalMarker, hasValidReasoningNews } from "@/lib/dashboard/signal-derivation";
import { signedTone, TONE_NEUTRAL } from "@/lib/dashboard/format";
import { cn } from "@/lib/utils";

const EMPTY_SIGNALS: FilteredSignal[] = [];
const EMPTY_HOLDINGS: EnrichedHolding[] = [];
const EMPTY_CANDLES: DashboardCandle[] = [];
const DASHBOARD_QUERY_KEY = ["dashboard"] as const;
const SIGNALS_QUERY_KEY = ["signals"] as const;

function timeGreeting(): string {
  const h = new Date().getHours();
  if (h < 12) return "Good morning";
  if (h < 18) return "Good afternoon";
  return "Good evening";
}

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

function getPortfolioValueDetail(marketDataUnavailable: boolean, partialMarketData: boolean): string {
  if (marketDataUnavailable) return "Market data unavailable";
  if (partialMarketData) return "Partial live pricing";
  return "Marked to market";
}

function getUnrealizedPnlDetail(marketDataUnavailable: boolean, partialMarketData: boolean): string {
  if (marketDataUnavailable) return "Market data unavailable";
  if (partialMarketData) return "Priced holdings only";
  return "Open position gains";
}

function getSignalTypeStyle(type: string): string {
  if (type === "BUY") return "ring-1 ring-buy-ring bg-buy/10 text-emerald-200 border-buy/40 shadow-buy-glow";
  if (type === "SELL") return "ring-1 ring-sell-ring bg-sell/10 text-rose-200 border-sell/40 shadow-sell-glow";
  return "ring-1 ring-hold-ring bg-hold/10 text-amber-200 border-hold/40 shadow-hold-glow";
}

function Sparkline({ values, color }: { readonly values: number[]; readonly color: string }) {
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

export default function DashboardHomePage() {
  const queryClient = useQueryClient();
  const { user } = useUser();
  const { data, isLoading, error } = useQuery({
    queryKey: DASHBOARD_QUERY_KEY,
    queryFn: fetchDashboardPageData,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    refetchInterval: 30_000,
    refetchOnMount: "always",
  });

  const portfolio = data?.portfolio ?? null;
  const signals = data?.signals ?? EMPTY_SIGNALS;
  const holdings = data?.holdings ?? EMPTY_HOLDINGS;
  const chartCandles = data?.chartCandles ?? EMPTY_CANDLES;

  const signalLogos = useStockLogos(useMemo(() => signals.map((s: FilteredSignal) => s.symbol), [signals]));

  const displayName = useMemo(
    () => user?.firstName ?? user?.primaryEmailAddress?.emailAddress?.split("@")[0] ?? "there",
    [user?.firstName, user?.primaryEmailAddress?.emailAddress]
  );

  const summaryCards = useMemo(() => {
    if (!portfolio) {
      return [];
    }

    const liveSignals = signals.filter((signal: FilteredSignal) => signal.live).length;
    const marketDataUnavailable = isMarketDataUnavailable(portfolio.dataSource);
    const partialMarketData = isPartialMarketData(portfolio.dataSource);
    const totalCapital = marketDataUnavailable ? null : (portfolio.totalCapital ?? 0);
    const unrealizedPnl = marketDataUnavailable ? null : (portfolio.unrealizedPnl ?? 0);

    return [
      {
        label: "Portfolio Value",
        value: totalCapital === null ? "N/A" : formatMoney(totalCapital),
        detail: getPortfolioValueDetail(marketDataUnavailable, partialMarketData),
        tone: "text-green",
      },
      { label: "Open Positions", value: `${holdings.length}`, detail: "Backend portfolio book", tone: "text-white" },
      {
        label: "Live Signals",
        value: `${liveSignals}`,
        detail: `${signals.length} total · ${liveSignals} within 24 h`,
        tone: "text-cyan",
        title: "Generated within the last 24 hours",
      },
      {
        label: "Unrealized P&L",
        value: unrealizedPnl === null ? "N/A" : formatSignedMoney(unrealizedPnl),
        detail: getUnrealizedPnlDetail(marketDataUnavailable, partialMarketData),
        tone: signedTone(unrealizedPnl, TONE_NEUTRAL),
      },
    ];
  }, [holdings.length, portfolio, signals]);

  const liveSignals = useMemo(() => signals.filter((s) => s.live), [signals]);
  const topLiveSignal = liveSignals[0] ?? null;
  const [selectedSignalId, setSelectedSignalId] = useState<string | null>(null);
  const selectedSignal = useMemo(
    () => liveSignals.find((s) => s.id === selectedSignalId) ?? topLiveSignal,
    [liveSignals, selectedSignalId, topLiveSignal]
  );
  const activeSymbol = selectedSignal?.symbol ?? null;

  const clearSelection = useCallback(() => setSelectedSignalId(null), []);
  useAgeOutToast(liveSignals, selectedSignalId, topLiveSignal, clearSelection);

  const { data: dynamicCandles } = useQuery<DashboardCandle[]>({
    queryKey: ["candles", activeSymbol],
    queryFn: async () => {
      if (!activeSymbol) return [];
      const res = await fetch(`/api/dashboard/candles?symbol=${encodeURIComponent(activeSymbol)}`);
      if (!res.ok) return [];
      return res.json() as Promise<DashboardCandle[]>;
    },
    enabled: !!activeSymbol && selectedSignal?.id !== topLiveSignal?.id,
    staleTime: 60_000,
  });
  const activeCandles = useMemo(
    () => (selectedSignal?.id === topLiveSignal?.id ? chartCandles : (dynamicCandles ?? [])),
    [selectedSignal?.id, topLiveSignal?.id, dynamicCandles, chartCandles]
  );
  const activeMarker = useMemo(
    () => buildSignalMarker(selectedSignal, activeCandles),
    [selectedSignal, activeCandles]
  );

  const [generateResult, setGenerateResult] = useState<string | null>(null);
  const generateSignals = useMutation({
    mutationFn: async () => {
      const tickers = Array.from(
        new Set([
          ...signals.map((s: FilteredSignal) => s.symbol),
          ...holdings.map((h: EnrichedHolding) => h.symbol),
        ])
      );
      const response = await fetch("/api/admin/generate-signals", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tickers: tickers.length > 0 ? tickers : ["AAPL", "MSFT", "GOOGL"] }),
      });
      if (!response.ok) {
        const err = (await response.json().catch(() => ({}))) as { message?: string };
        throw new Error(err.message ?? `Error ${response.status}`);
      }
      return response.json() as Promise<{ predictions: { ticker: string; direction: string }[] }>;
    },
    onSuccess: (result) => {
      const count = result.predictions?.length ?? 0;
      setGenerateResult(`${count} signal${count > 1 ? "s" : ""} queued for persistence`);
      queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: SIGNALS_QUERY_KEY });
      setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEY });
        queryClient.invalidateQueries({ queryKey: SIGNALS_QUERY_KEY });
      }, 3000);
      setTimeout(() => setGenerateResult(null), 5000);
    },
  });

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
      <section className="rounded-[24px] border border-border bg-bg-1/80 bg-gradient-hero p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="inline-flex items-center gap-1.5 font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">
              <LiveLed label="LIVE · Overview" />
            </div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              <span className="bg-gradient-to-r from-cyan to-green bg-clip-text text-transparent">
                {timeGreeting()}
              </span>
              {`, ${displayName}`}
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-text-2">
              {signals.length} signals tracked · {holdings.length} open positions
            </p>
            {isMarketDataUnavailable(portfolio.dataSource) && (
              <p className="mt-3 text-sm text-gold">
                Market pricing is currently unavailable. Holdings remain visible, but current prices and P&amp;L are paused.
              </p>
            )}
            {isPartialMarketData(portfolio.dataSource) && !isMarketDataUnavailable(portfolio.dataSource) && (
              <p className="mt-3 text-sm text-gold">
                Partial pricing returned from market data. Some holdings and signals are still waiting on fresh prices.
              </p>
            )}
            {(user?.publicMetadata as { role?: string } | undefined)?.role === "admin" && (
              <div className="mt-4 flex items-center gap-3">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={generateSignals.isPending}
                  onClick={() => generateSignals.mutate()}
                  className="border-cyan/30 text-cyan hover:bg-cyan/10"
                >
                  {generateSignals.isPending ? "Generating..." : "Generate Signals"}
                </Button>
                <Link
                  href="/dashboard/admin/reasoning-audit"
                  className="text-xs text-cyan underline-offset-4 hover:underline"
                >
                  Reasoning audit →
                </Link>
                {generateSignals.isError && (
                  <span className="text-xs text-red">
                    {generateSignals.error instanceof Error ? generateSignals.error.message : "Failed"}
                  </span>
                )}
                {generateResult && <span className="text-xs text-green">{generateResult}</span>}
              </div>
            )}
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
            <article key={card.label} className="rounded-[20px] border border-border bg-bg-2 p-5 hover:shadow-neon-soft transition-shadow duration-200" title={card.title}>
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
                {liveSignals.length === 0
                  ? "No live signals right now"
                  : selectedSignal
                    ? `${selectedSignal.symbol} ${selectedSignal.type} ${selectedSignal.timeframe}`
                    : "Live chart"}
              </h3>
            </div>
            <Button asChild variant="outlineCyan" size="sm">
              <Link href="/dashboard/signals">
                View signals
                <ArrowRightIcon className="ml-2 h-4 w-4" />
              </Link>
            </Button>
          </div>

          {liveSignals.length > 0 && (
            <div className="mt-4">
              <LiveSignalsStrip
                signals={liveSignals}
                selectedSignalId={selectedSignalId ?? ""}
                onSignalChange={setSelectedSignalId}
              />
            </div>
          )}

          <div className="mt-4 rounded-[22px] border border-border bg-bg-0/70 p-3">
            {liveSignals.length === 0 ? (
              <ChartPlaceholder>No live signals in the last 24 hours.</ChartPlaceholder>
            ) : activeCandles.length > 0 ? (
              <CandlestickChart candles={activeCandles} markers={activeMarker ? [activeMarker] : undefined} showVolume={false} height={320} />
            ) : (
              <ChartPlaceholder>Loading chart data…</ChartPlaceholder>
            )}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Live signals</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Recent AI decisions</h3>

          <div className="mt-6 space-y-4">
            {signals.filter(hasValidReasoningNews).slice(0, 4).map((signal) => (
              <AiDecisionCard
                key={signal.id}
                signal={signal}
                logoUrl={signalLogos?.[signal.symbol]}
                typeBadgeClass={getSignalTypeStyle(signal.type)}
              />
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
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">
                      {position.lastPrice ? formatMoney(position.lastPrice) : "N/A"}
                    </td>
                    <td className={`border-t border-border px-4 py-4 font-mono ${signedTone(pnl, TONE_NEUTRAL)}`}>
                      {pnl === null ? "N/A" : formatSignedMoney(pnl)}
                    </td>
                    <td className="border-t border-border px-4 py-4 text-text-2">{position.sector}</td>
                    <td className="border-t border-border px-4 py-4">
                      {position.trend.length > 0
                        ? <Sparkline values={position.trend} color={position.color} />
                        : <span className="text-text-3 text-xs" title="Awaiting price history">—</span>}
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

function ChartPlaceholder({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-[320px] items-center justify-center rounded-[18px] border border-dashed border-border text-sm text-text-2">
      {children}
    </div>
  );
}
