"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo } from "react";

import { DeepAnalysisCard } from "@/components/dashboard/deep-analysis-card";
import { SignalChart } from "@/components/dashboard/signal-chart";
import { apiClient } from "@/lib/api-client";
import { ArrowRightIcon } from "@/components/site/icons";
import { Button } from "@/components/ui/button";
import { StockLogo } from "@/components/ui/stock-logo";
import { fetchSignalDetailData } from "@/lib/dashboard/client-data";
import { deriveSignal } from "@/lib/dashboard/signal-derivation";
import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";
import { formatPredictedChange } from "@/lib/signal-utils";
import { scrubReasoningText } from "@/lib/signal-reasoning-format";

type SignalDetailClientProps = {
  signalId: string;
};

const EMPTY_CANDLES: ChartCandle[] = [];

function formatPrice(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }

  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

function deriveStatus(generatedAt: string): "NEW" | "LIVE" | "ACTIVE" {
  const ageMs = Date.now() - new Date(generatedAt).getTime();
  if (ageMs < 60 * 60 * 1000) return "NEW";
  if (ageMs < 24 * 60 * 60 * 1000) return "LIVE";
  return "ACTIVE";
}

function formatConfidence(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function formatSignalDate(value: string) {
  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}


export function SignalDetailClient({ signalId }: SignalDetailClientProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["signal", signalId],
    queryFn: () => fetchSignalDetailData(signalId),
  });
  const signal = data?.signal ?? null;
  const latestPrice = data?.latestPrice ?? null;
  const candles = data?.candles ?? EMPTY_CANDLES;

  const { data: user } = useQuery({
    queryKey: ["me"],
    queryFn: () => apiClient.getCurrentUser(),
    staleTime: 5 * 60 * 1000,
  });
  const isPremium = (user?.plan ?? "").toUpperCase() === "PREMIUM";

  const { data: logoData } = useQuery<Record<string, string | null>>({
    queryKey: ["logos", signal?.symbol ? [signal.symbol] : []],
    queryFn: async () => {
      if (!signal) return {};
      // Client-side apiClient path (session Bearer) — same proven
      // mechanism as useStockLogos. The old /api/stocks/logos server
      // route never authenticated and returned all-null.
      const logo = await apiClient.getCompanyLogo(signal.symbol);
      return { [signal.symbol]: logo };
    },
    enabled: !!signal,
    staleTime: 60 * 60 * 1000,
  });

  const marker: ChartMarker | null = useMemo(() => {
    if (!signal || candles.length === 0) {
      return null;
    }

    return {
      time: candles[candles.length - 1].time,
      position: signal.type === "SELL" ? "aboveBar" : "belowBar",
      color: signal.type === "SELL" ? "#ff4d6a" : signal.type === "BUY" ? "#00d68f" : "#e8b84b",
      shape: signal.type === "SELL" ? "arrowDown" : signal.type === "BUY" ? "arrowUp" : "circle",
      text: signal.type,
    };
  }, [candles, signal]);

  const derived = signal ? deriveSignal(signal, latestPrice) : null;
  const entry = derived?.entry ?? null;
  const takeProfit = derived?.takeProfit ?? null;
  const stopLoss = derived?.stopLoss ?? null;

  const reasoning = useMemo(() => {
    if (!signal) {
      return "";
    }

    if (
      (signal.reasoningStatus === "READY" || signal.reasoningStatus === "FALLBACK") &&
      signal.reasoning
    ) {
      return scrubReasoningText(signal.reasoning, signal);
    }

    const predicted = derived?.expectedMovePct ?? 0;
    const priceText = formatPrice(entry);

    if (signal.type === "BUY") {
      return `Bullish continuation setup around ${priceText} with ${Math.abs(predicted).toFixed(1)}% projected upside and ${formatConfidence(signal.confidence)} confidence.`;
    }

    if (signal.type === "SELL") {
      return `Bearish breakdown setup around ${priceText} with ${Math.abs(predicted).toFixed(1)}% projected downside and ${formatConfidence(signal.confidence)} confidence.`;
    }

    return `Neutral setup around ${priceText} while the model waits for a cleaner directional edge.`;
  }, [derived, entry, signal]);

  if (isLoading) {
    return (
      <div className="space-y-8">
        <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="h-8 w-48 animate-pulse rounded-full bg-bg-2" />
          <div className="mt-4 h-12 w-64 animate-pulse rounded-full bg-bg-2" />
        </section>
        <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="h-[520px] animate-pulse rounded-[24px] bg-bg-1/80" />
          <div className="h-[520px] animate-pulse rounded-[24px] bg-bg-1/80" />
        </section>
      </div>
    );
  }

  if (error || !signal) {
    return (
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">Signal detail</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Unable to load signal
        </h2>
        <p className="mt-3 text-sm leading-7 text-text-2">
          {error instanceof Error ? error.message : "The signal could not be found."}
        </p>
        <Button asChild variant="outlineCyan" size="sm" className="mt-6">
          <Link href="/dashboard/signals">
            Back to signals
            <ArrowRightIcon className="ml-2 h-4 w-4 rotate-180" />
          </Link>
        </Button>
      </section>
    );
  }

  const status = deriveStatus(signal.generatedAt);

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Signal detail</div>
            <div className="mt-3 flex items-center gap-3">
              <StockLogo ticker={signal.symbol} logoUrl={logoData?.[signal.symbol]} size={40} />
              <h2 className="font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
                {signal.symbol}
              </h2>
            </div>
            <p className="mt-2 text-sm uppercase tracking-[0.22em] text-text-3">
              {signal.type} · {signal.timeframe}
            </p>
          </div>

          <Button asChild variant="outlineCyan" size="sm">
            <Link href="/dashboard/signals">
              Back to signals
              <ArrowRightIcon className="ml-2 h-4 w-4 rotate-180" />
            </Link>
          </Button>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Chart preview</div>
              <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
                Price action and marker
              </h3>
            </div>
            <span className="rounded-full border border-cyan/25 bg-cyan-dim px-3 py-1 text-[10px] uppercase tracking-[0.22em] text-cyan">
              {status}
            </span>
          </div>

          <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
            {marker ? <SignalChart candles={candles} marker={marker} /> : null}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Signal summary</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Why this signal</h3>

          <div className="mt-6 space-y-4">
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-xs uppercase tracking-[0.22em] text-text-3">Confidence</div>
              <div className="mt-2 font-mono text-2xl text-white">{formatConfidence(signal.confidence)}</div>
            </div>
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-xs uppercase tracking-[0.22em] text-text-3">Reference price</div>
              <div className="mt-2 font-mono text-2xl text-white">{formatPrice(entry)}</div>
            </div>
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-xs uppercase tracking-[0.22em] text-text-3">Generated</div>
              <div className="mt-2 font-mono text-2xl text-white">{formatSignalDate(signal.generatedAt)}</div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-2xl border border-border bg-bg-2 p-4">
                <div className="text-xs uppercase tracking-[0.22em] text-text-3">Entry</div>
                <div className="mt-2 font-mono text-lg text-white">
                  {signal?.entryPrice == null && entry == null
                    ? <span title="Entry price not captured for legacy signals" className="cursor-help text-text-3">—</span>
                    : formatPrice(entry)}
                </div>
              </div>
              <div className="rounded-2xl border border-border bg-bg-2 p-4">
                <div className="text-xs uppercase tracking-[0.22em] text-text-3">Target</div>
                <div className="mt-2 font-mono text-lg text-green">{formatPrice(takeProfit)}</div>
              </div>
              <div className="rounded-2xl border border-border bg-bg-2 p-4">
                <div className="text-xs uppercase tracking-[0.22em] text-text-3">Stop</div>
                <div className="mt-2 font-mono text-lg text-red">{formatPrice(stopLoss)}</div>
              </div>
              <div className="rounded-2xl border border-border bg-bg-2 p-4">
                <div className="text-xs uppercase tracking-[0.22em] text-text-3">State</div>
                <div className="mt-2 font-mono text-lg text-white">{status}</div>
              </div>
            </div>
          </div>

          <div className="mt-6 rounded-[20px] border border-cyan/30 bg-cyan/[0.04] shadow-neon-soft p-5">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Signal Rationale</div>
            {!signal.reasoningStatus || signal.reasoningStatus === "PENDING" ? (
              <div className="mt-3 space-y-2">
                <div className="h-3 w-full animate-pulse rounded-full bg-bg-2" />
                <div className="h-3 w-4/5 animate-pulse rounded-full bg-bg-2" />
                <div className="h-3 w-3/5 animate-pulse rounded-full bg-bg-2" />
              </div>
            ) : (
              <p className="mt-3 text-sm leading-7 text-text-1">{reasoning}</p>
            )}
          </div>

          {(() => {
            const movePct = derived?.expectedMovePct ?? null;
            const { label, colorClass } = formatPredictedChange(movePct, signal.type);
            const pct = movePct ?? 0;
            const barWidth = Math.min(Math.abs(pct) / 10, 1) * 100;
            let barColor: string;
            switch (signal.type) {
              case "BUY": barColor = "bg-emerald-400"; break;
              case "SELL": barColor = "bg-rose-400"; break;
              default: barColor = "bg-amber-400";
            }
            return (
              <div className="mt-6 rounded-2xl border border-border bg-bg-2 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className={`font-mono text-2xl font-bold ${colorClass}`}>{label}</div>
                    <div className="mt-1 text-xs uppercase tracking-[0.22em] text-text-3">Expected move</div>
                  </div>
                  <div className="flex-1 max-w-[140px]">
                    <div
                      className="mt-1 h-2 rounded-full bg-bg-3 overflow-hidden"
                      title={entry == null || takeProfit == null ? undefined : `From ${formatPrice(entry)} to ${formatPrice(takeProfit)}`}
                    >
                      <div
                        className={`h-full rounded-full ${barColor}`}
                        style={{ width: `${barWidth}%` }}
                      />
                    </div>
                    <div className="mt-1 text-[10px] text-text-3">0% → {Math.abs(pct).toFixed(1)}%</div>
                  </div>
                </div>
              </div>
            );
          })()}
        </article>
      </section>

      <DeepAnalysisCard signalId={signalId} isPremium={isPremium} />
    </div>
  );
}
