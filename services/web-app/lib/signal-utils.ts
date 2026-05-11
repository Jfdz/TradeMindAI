import type { SignalResponse } from "./api-client";

export function formatConfidence(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

export function formatSignalAge(generatedAt: string): string {
  const ts = new Date(generatedAt).getTime();
  if (Number.isNaN(ts)) return "recently";

  const diffMinutes = Math.max(Math.round((Date.now() - ts) / 60_000), 0);
  if (diffMinutes < 60) return `${Math.max(diffMinutes, 1)}m ago`;

  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;

  return `${Math.max(Math.round(diffHours / 24), 1)}d ago`;
}

export function signalTypeColor(type: SignalResponse["type"]): string {
  if (type === "BUY") return "#00d68f";
  if (type === "SELL") return "#ff4d6a";
  return "#e8b84b";
}

export function formatPredictedChange(
  pct: number | null | undefined,
  type: string
): { label: string; colorClass: string } {
  const val = pct ?? 0;
  if (type === "BUY") return { label: `▲ +${Math.abs(val).toFixed(2)}%`, colorClass: "text-emerald-400" };
  if (type === "SELL") return { label: `▼ −${Math.abs(val).toFixed(2)}%`, colorClass: "text-rose-400" };
  return { label: `→ ${val.toFixed(2)}%`, colorClass: "text-amber-400" };
}

export function buildSignalReasoning(signal: SignalResponse, latestPrice: number | null): string {
  if (
    (signal.reasoningStatus === "READY" || signal.reasoningStatus === "FALLBACK") &&
    signal.reasoning
  ) {
    return signal.reasoning;
  }

  const predicted = signal.predictedChangePct ?? 0;
  const move = `${Math.abs(predicted).toFixed(1)}%`;
  const priceText =
    latestPrice == null
      ? "the latest market price"
      : `$${latestPrice.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  if (signal.type === "BUY") {
    return `Bullish continuation setup around ${priceText} with ${move} projected upside and ${formatConfidence(signal.confidence)} confidence.`;
  }
  if (signal.type === "SELL") {
    return `Bearish breakdown setup around ${priceText} with ${move} projected downside and ${formatConfidence(signal.confidence)} confidence.`;
  }
  return `Neutral setup near ${priceText} while the model waits for a stronger directional edge.`;
}
