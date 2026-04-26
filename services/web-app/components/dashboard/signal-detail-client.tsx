"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import { SignalChart } from "@/components/dashboard/signal-chart";
import { ArrowRightIcon } from "@/components/site/icons";
import { Button } from "@/components/ui/button";
import { apiClient, type MarketPriceResponse, type SignalResponse } from "@/lib/api-client";
import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";

type SignalDetailClientProps = {
  signalId: string;
};

function toBusinessDay(dateValue: string): ChartCandle["time"] {
  const date = new Date(dateValue);

  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  };
}

function buildSyntheticCandles(signal: SignalResponse, basePrice: number): ChartCandle[] {
  return Array.from({ length: 12 }, (_, index) => {
    const drift = (index - 6) * (signal.type === "SELL" ? -1.1 : 1.1);
    const open = Number((basePrice - 6 + drift).toFixed(2));
    const close = Number((open + (index % 2 === 0 ? 1.8 : -1.1)).toFixed(2));
    const high = Number((Math.max(open, close) + 1.7).toFixed(2));
    const low = Number((Math.min(open, close) - 1.4).toFixed(2));
    const day = new Date(signal.generatedAt);
    day.setUTCDate(day.getUTCDate() + index - 6);

    return {
      time: toBusinessDay(day.toISOString()),
      open,
      high,
      low,
      close,
      volume: 700000 + index * 72000,
    };
  });
}

function buildCandles(signal: SignalResponse, history: MarketPriceResponse[], fallbackPrice: number): ChartCandle[] {
  if (history.length > 0) {
    return history
      .slice()
      .reverse()
      .map((bar) => ({
        time: toBusinessDay(bar.date),
        open: bar.ohlcv.open,
        high: bar.ohlcv.high,
        low: bar.ohlcv.low,
        close: bar.ohlcv.close,
        volume: bar.ohlcv.volume,
      }));
  }

  return buildSyntheticCandles(signal, fallbackPrice);
}

function formatPrice(value: number | null | undefined) {
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
  const [signal, setSignal] = useState<SignalResponse | null>(null);
  const [latestPrice, setLatestPrice] = useState<number | null>(null);
  const [candles, setCandles] = useState<ChartCandle[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadSignal() {
      try {
        const response = await apiClient.getSignal(signalId);
        const latest = await apiClient.getLatestPrice(response.symbol);
        const latestClose = latest?.adjustedClose ?? latest?.ohlcv.close ?? null;
        const from = new Date(response.generatedAt);
        from.setUTCDate(from.getUTCDate() - 10);
        const historical = await apiClient.getHistoricalPrices(
          response.symbol,
          from.toISOString().slice(0, 10),
          new Date().toISOString().slice(0, 10),
          18
        );

        if (!mounted) {
          return;
        }

        setSignal(response);
        setLatestPrice(latestClose);
        setCandles(buildCandles(response, historical.content, latestClose ?? response.predictedChangePct ?? 0));
      } catch (requestError) {
        if (mounted) {
          setError(requestError instanceof Error ? requestError.message : "Unable to load signal");
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    }

    loadSignal();

    return () => {
      mounted = false;
    };
  }, [signalId]);

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

  const entry = latestPrice;
  const takeProfit =
    signal?.type === "BUY"
      ? signal.takeProfitPct != null && entry != null
        ? entry * (1 + signal.takeProfitPct / 100)
        : null
      : signal?.type === "SELL"
        ? signal?.takeProfitPct != null && entry != null
          ? entry * (1 - signal.takeProfitPct / 100)
          : null
        : entry;
  const stopLoss =
    signal?.type === "BUY"
      ? signal.stopLossPct != null && entry != null
        ? entry * (1 - signal.stopLossPct / 100)
        : null
      : signal?.type === "SELL"
        ? signal.stopLossPct != null && entry != null
          ? entry * (1 + signal.stopLossPct / 100)
          : null
        : entry;

  const reasoning = useMemo(() => {
    if (!signal) {
      return "";
    }

    const predicted = signal.predictedChangePct ?? 0;
    const priceText = formatPrice(entry);

    if (signal.type === "BUY") {
      return `Bullish continuation setup around ${priceText} with ${Math.abs(predicted).toFixed(1)}% projected upside and ${formatConfidence(signal.confidence)} confidence.`;
    }

    if (signal.type === "SELL") {
      return `Bearish breakdown setup around ${priceText} with ${Math.abs(predicted).toFixed(1)}% projected downside and ${formatConfidence(signal.confidence)} confidence.`;
    }

    return `Neutral setup around ${priceText} while the model waits for a cleaner directional edge.`;
  }, [entry, signal]);

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
        <p className="mt-3 text-sm leading-7 text-text-2">{error ?? "The signal could not be found."}</p>
        <Button asChild variant="outlineCyan" size="sm" className="mt-6">
          <Link href="/dashboard/signals">
            Back to signals
            <ArrowRightIcon className="ml-2 h-4 w-4 rotate-180" />
          </Link>
        </Button>
      </section>
    );
  }

  const live = Date.now() - new Date(signal.generatedAt).getTime() < 1000 * 60 * 60 * 24;

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Signal detail</div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              {signal.symbol}
            </h2>
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
              {live ? "LIVE" : "PENDING"}
            </span>
          </div>

          <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
            {marker ? <SignalChart candles={candles} marker={marker} /> : null}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Signal summary</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">TradeMind rationale</h3>

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
                <div className="mt-2 font-mono text-lg text-white">{formatPrice(entry)}</div>
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
                <div className="mt-2 font-mono text-lg text-white">{live ? "LIVE" : "PENDING"}</div>
              </div>
            </div>
          </div>

          <div className="mt-6 rounded-[20px] border border-cyan/25 bg-cyan-dim p-5">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Reasoning</div>
            <p className="mt-3 text-sm leading-7 text-text-1">{reasoning}</p>
          </div>

          <div className="mt-6 rounded-2xl border border-border bg-bg-2 p-4 text-sm leading-7 text-text-2">
            Predicted change: {(signal.predictedChangePct ?? 0).toFixed(2)}%
          </div>
        </article>
      </section>
    </div>
  );
}
