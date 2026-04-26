"use client";

import { useEffect, useMemo, useState } from "react";

import { apiClient, type SignalResponse } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type FilterValue = "ALL" | "BUY" | "SELL" | "HOLD";

type ResolvedSignal = SignalResponse & {
  latestPrice: number | null;
  entry: number | null;
  takeProfit: number | null;
  stopLoss: number | null;
  live: boolean;
  status: "LIVE" | "PENDING";
  age: string;
  generatedLabel: string;
  reasoning: string;
};

const filterOptions: FilterValue[] = ["ALL", "BUY", "SELL", "HOLD"];

function formatPrice(value: number | null) {
  if (value == null || Number.isNaN(value)) {
    return "N/A";
  }

  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

function formatConfidence(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function formatAge(value: string) {
  const generatedAt = new Date(value).getTime();
  if (Number.isNaN(generatedAt)) {
    return "recently";
  }

  const diffMinutes = Math.max(Math.round((Date.now() - generatedAt) / 60000), 0);
  if (diffMinutes < 60) {
    return `${Math.max(diffMinutes, 1)}m ago`;
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }

  return `${Math.max(Math.round(diffHours / 24), 1)}d ago`;
}

function buildReasoning(signal: SignalResponse, latestPrice: number | null) {
  const predicted = signal.predictedChangePct ?? 0;
  const move = `${Math.abs(predicted).toFixed(1)}%`;
  const priceText = latestPrice == null ? "the latest market price" : formatPrice(latestPrice);

  if (signal.type === "BUY") {
    return `Bullish continuation setup around ${priceText} with ${move} projected upside and ${formatConfidence(signal.confidence)} confidence.`;
  }

  if (signal.type === "SELL") {
    return `Bearish breakdown setup around ${priceText} with ${move} projected downside and ${formatConfidence(signal.confidence)} confidence.`;
  }

  return `Neutral setup near ${priceText} while the model waits for a stronger directional edge.`;
}

function deriveSignal(signal: SignalResponse, latestPrice: number | null): ResolvedSignal {
  const entry = latestPrice;
  const takeProfit =
    signal.type === "BUY"
      ? signal.takeProfitPct != null && entry != null
        ? entry * (1 + signal.takeProfitPct / 100)
        : null
      : signal.type === "SELL"
        ? signal.takeProfitPct != null && entry != null
          ? entry * (1 - signal.takeProfitPct / 100)
          : null
        : entry;
  const stopLoss =
    signal.type === "BUY"
      ? signal.stopLossPct != null && entry != null
        ? entry * (1 - signal.stopLossPct / 100)
        : null
      : signal.type === "SELL"
        ? signal.stopLossPct != null && entry != null
          ? entry * (1 + signal.stopLossPct / 100)
          : null
        : entry;
  const live = Date.now() - new Date(signal.generatedAt).getTime() < 1000 * 60 * 60 * 24;

  return {
    ...signal,
    latestPrice,
    entry,
    takeProfit,
    stopLoss,
    live,
    status: live ? "LIVE" : "PENDING",
    age: formatAge(signal.generatedAt),
    generatedLabel: new Date(signal.generatedAt).toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }),
    reasoning: buildReasoning(signal, latestPrice),
  };
}

export default function SignalsPage() {
  const [activeFilter, setActiveFilter] = useState<FilterValue>("ALL");
  const [signals, setSignals] = useState<ResolvedSignal[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadSignals() {
      try {
        const response = await apiClient.getSignals();
        const content = response.content ?? [];
        const uniqueSymbols = Array.from(new Set(content.map((signal) => signal.symbol)));
        const latestPriceBySymbol = new Map<string, number | null>();

        await Promise.all(
          uniqueSymbols.map(async (symbol) => {
            const latest = await apiClient.getLatestPrice(symbol);
            latestPriceBySymbol.set(symbol, latest?.adjustedClose ?? latest?.ohlcv.close ?? null);
          })
        );

        if (!mounted) {
          return;
        }

        setSignals(content.map((signal) => deriveSignal(signal, latestPriceBySymbol.get(signal.symbol) ?? null)));
      } catch (requestError) {
        if (mounted) {
          setError(requestError instanceof Error ? requestError.message : "Unable to load signals");
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    }

    loadSignals();

    return () => {
      mounted = false;
    };
  }, []);

  const filteredSignals = useMemo(() => {
    if (activeFilter === "ALL") {
      return signals;
    }

    return signals.filter((signal) => signal.type === activeFilter);
  }, [activeFilter, signals]);

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="inline-flex items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
              <span className="h-2 w-2 rounded-full bg-green animate-pulse-soft" />
              Live signals
            </div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              Updating from the backend
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-text-2">
              Review the latest signal feed, narrow the list with filters, and open a full signal detail page for more
              context.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {filterOptions.map((filter) => (
              <Button
                key={filter}
                size="sm"
                variant={activeFilter === filter ? "cyan" : "outline"}
                onClick={() => setActiveFilter(filter)}
              >
                {filter}
              </Button>
            ))}
          </div>
        </div>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        {isLoading ? (
          <div className="space-y-4">
            <div className="h-8 w-64 animate-pulse rounded-full bg-bg-2" />
            <div className="h-[360px] animate-pulse rounded-[20px] bg-bg-2" />
          </div>
        ) : error ? (
          <div className="rounded-2xl border border-red/30 bg-red/10 p-4 text-sm text-red">
            {error}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-[1200px] w-full border-separate border-spacing-0">
              <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                <tr>
                  <th className="px-4 py-3 text-left">Pair</th>
                  <th className="px-4 py-3 text-left">Signal</th>
                  <th className="px-4 py-3 text-left">TF</th>
                  <th className="px-4 py-3 text-left">Entry</th>
                  <th className="px-4 py-3 text-left">Take Profit</th>
                  <th className="px-4 py-3 text-left">Stop Loss</th>
                  <th className="px-4 py-3 text-left">Confidence</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-left">Reasoning</th>
                  <th className="px-4 py-3 text-left">Time</th>
                </tr>
              </thead>
              <tbody>
                {filteredSignals.map((signal, index) => (
                  <tr
                    key={signal.id}
                    className={cn("transition hover:bg-white/[0.025]", index % 2 === 0 ? "bg-white/[0.012]" : "")}
                  >
                    <td className="border-t border-border px-4 py-4">
                      <div className="font-display text-base font-semibold tracking-[-0.03em] text-white">{signal.symbol}</div>
                      <div className="mt-1 text-xs uppercase tracking-[0.22em] text-text-3">{signal.age}</div>
                    </td>
                    <td className="border-t border-border px-4 py-4">
                      <span
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
                      </span>
                    </td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{signal.timeframe}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatPrice(signal.entry)}</td>
                    <td className="border-t border-border px-4 py-4 font-mono text-green">
                      {formatPrice(signal.takeProfit)}
                    </td>
                    <td className="border-t border-border px-4 py-4 font-mono text-red">
                      {formatPrice(signal.stopLoss)}
                    </td>
                    <td className="border-t border-border px-4 py-4">
                      <div className="w-44">
                        <div className="flex items-center justify-between text-xs text-text-2">
                          <span>{formatConfidence(signal.confidence)}</span>
                          <span>{signal.live ? "LIVE" : "PENDING"}</span>
                        </div>
                        <div className="mt-2 h-2 overflow-hidden rounded-full bg-bg-3">
                          <div
                            className="h-full rounded-full bg-gradient-to-r from-cyan to-cyan/60"
                            style={{ width: `${signal.confidence * 100}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td className="border-t border-border px-4 py-4">
                      <span
                        className={cn(
                          "rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.22em]",
                          signal.status === "LIVE"
                            ? "border-cyan/30 bg-cyan-dim text-cyan"
                            : "border-border bg-bg-2 text-text-2"
                        )}
                      >
                        {signal.status}
                      </span>
                    </td>
                    <td className="border-t border-border px-4 py-4 text-sm leading-6 text-text-2">{signal.reasoning}</td>
                    <td className="border-t border-border px-4 py-4">
                      <div className="font-mono text-text-1">{signal.age}</div>
                      <div className="mt-1 text-xs text-text-3">{signal.generatedLabel}</div>
                    </td>
                  </tr>
                ))}
                {filteredSignals.length === 0 ? (
                  <tr>
                    <td className="border-t border-border px-4 py-10 text-center text-sm text-text-2" colSpan={10}>
                      No signals match the current filter.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
