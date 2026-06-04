import type { SeriesMarker, Time } from "lightweight-charts";
import type { MarketPriceResponse, SignalResponse } from "@/lib/api-client";
import { buildSignalReasoning, resolveExpectedMovePct, signalTypeColor } from "@/lib/signal-utils";
import { hasOwnImage } from "@/lib/enrichment/news-image-filter";
import type { DashboardCandle, FilteredSignal } from "@/lib/dashboard/dashboard-api";

export function hasValidReasoningNews(signal: SignalResponse): boolean {
  return signal.reasoningNews != null && hasOwnImage(signal.reasoningNews.imageUrl);
}

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

// Best price the trade reached since generation: highest daily high for a
// LONG (BUY), lowest daily low for a SHORT (SELL), derived from the evaluator's
// max-favorable-excursion fraction (maxProfit). Null when not yet evaluated or HOLD.
export function computePeakPrice(
  signalType: string,
  entryPrice: number | null | undefined,
  maxProfit: number | null | undefined
): number | null {
  if (entryPrice == null || maxProfit == null) return null;
  if (signalType === "BUY") return entryPrice * (1 + maxProfit);
  if (signalType === "SELL") return entryPrice * (1 - maxProfit);
  return null; // HOLD has no directional target
}

// Color shares the value's basis (peak-vs-target), NOT the stop-aware outcome:
// a green number must always be at/through the target. BUY beats when the peak
// reaches the target; SELL beats when the trough drops to it.
export function pickPeakColor(
  signalType: string,
  peak: number | null,
  takeProfit: number | null
): string {
  if (peak == null || takeProfit == null) return "text-text-1";
  if (signalType === "BUY") return peak >= takeProfit ? "text-green" : "text-red";
  if (signalType === "SELL") return peak <= takeProfit ? "text-green" : "text-red";
  return "text-text-1";
}

function isLiveSignal(generatedAt: string): boolean {
  const ageMs = Date.now() - new Date(generatedAt).getTime();
  return ageMs < 1000 * 60 * 60 * 24;
}

function deriveStatus(generatedAt: string): "NEW" | "LIVE" | "ACTIVE" {
  const ageMs = Date.now() - new Date(generatedAt).getTime();
  if (ageMs < 60 * 60 * 1000) return "NEW";
  if (ageMs < 24 * 60 * 60 * 1000) return "LIVE";
  return "ACTIVE";
}

export function deriveSignal(signal: SignalResponse, latestPrice: number | null): FilteredSignal {
  const entry = signal.entryPrice ?? latestPrice;
  const takeProfit =
    signal.targetPrice ?? calculateTakeProfit(signal.type, signal.takeProfitPct, entry);
  const stopLoss =
    signal.stopLoss ?? calculateStopLoss(signal.type, signal.stopLossPct, entry);
  const expectedMovePct = resolveExpectedMovePct(entry, takeProfit, signal.predictedChangePct);
  const live = isLiveSignal(signal.generatedAt);

  return {
    ...signal,
    latestPrice,
    entry,
    takeProfit,
    stopLoss,
    expectedMovePct,
    live,
    status: deriveStatus(signal.generatedAt),
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

export function buildSignalMarker(
  signal: FilteredSignal | null,
  candles: DashboardCandle[]
): SeriesMarker<Time> | null {
  if (!signal || candles.length === 0 || signal.type === "HOLD") return null;
  const last = candles[candles.length - 1];
  return {
    time: last.time as Time,
    position: signal.type === "SELL" ? "aboveBar" : "belowBar",
    color: signalTypeColor(signal.type),
    shape: signal.type === "SELL" ? "arrowDown" : "arrowUp",
    text: signal.symbol,
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
