import type { MarketPriceResponse, SignalResponse } from "@/lib/api-client";
import { buildSignalReasoning } from "@/lib/signal-utils";
import type { DashboardCandle, FilteredSignal } from "@/lib/dashboard/dashboard-api";

export function formatAge(value: string) {
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

export function toBusinessDay(value: string): DashboardCandle["time"] {
  const date = new Date(value);

  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  };
}

function calculateTakeProfit(
  signalType: string,
  takeProfitPct: number | null | undefined,
  entry: number | null
): number | null {
  if (entry === null || takeProfitPct == null) return null;

  switch (signalType) {
    case "BUY":
      return entry * (1 + takeProfitPct / 100);
    case "SELL":
      return entry * (1 - takeProfitPct / 100);
    default:
      return null;
  }
}

function calculateStopLoss(
  signalType: string,
  stopLossPct: number | null | undefined,
  entry: number | null
): number | null {
  if (entry === null || stopLossPct == null) return null;

  switch (signalType) {
    case "BUY":
      return entry * (1 - stopLossPct / 100);
    case "SELL":
      return entry * (1 + stopLossPct / 100);
    default:
      return null;
  }
}

function isLiveSignal(generatedAt: string): boolean {
  const ageMs = Date.now() - new Date(generatedAt).getTime();
  return ageMs < 1000 * 60 * 60 * 24;
}

export function deriveSignal(signal: SignalResponse, latestPrice: number | null): FilteredSignal {
  const entry = signal.entryPrice ?? latestPrice;
  const takeProfit = calculateTakeProfit(signal.type, signal.takeProfitPct, entry);
  const stopLoss = calculateStopLoss(signal.type, signal.stopLossPct, entry);
  const live = isLiveSignal(signal.generatedAt);

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
    reasoning: buildSignalReasoning(signal, latestPrice),
  };
}

export function convertPricesToCandles(prices: MarketPriceResponse[]): DashboardCandle[] {
  return prices
    .slice()
    .sort((left, right) => new Date(left.date).getTime() - new Date(right.date).getTime())
    .map((price) => ({
      time: toBusinessDay(price.date),
      open: price.ohlcv.open,
      high: price.ohlcv.high,
      low: price.ohlcv.low,
      close: price.adjustedClose ?? price.ohlcv.close,
      volume: price.ohlcv.volume,
    }));
}
