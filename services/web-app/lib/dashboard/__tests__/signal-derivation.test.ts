import { describe, expect, it } from "vitest";
import { buildSignalMarker } from "../signal-derivation";
import type { DashboardCandle } from "../dashboard-api";
import type { FilteredSignal } from "../dashboard-api";

function makeCandle(year: number, month: number, day: number): DashboardCandle {
  return { time: { year, month, day }, open: 100, high: 105, low: 95, close: 102, volume: 1_000_000 };
}

function makeSignal(type: "BUY" | "SELL" | "HOLD", symbol = "AAPL"): FilteredSignal {
  return {
    id: "sig-1",
    symbol,
    type,
    timeframe: "1D",
    confidence: 0.8,
    entryPrice: null,
    takeProfitPct: 5,
    stopLossPct: 3,
    predictedChangePct: 4,
    reasoning: "test",
    reasoningStatus: "READY",
    generatedAt: new Date().toISOString(),
    latestPrice: 100,
    entry: 100,
    takeProfit: 105,
    stopLoss: 97,
    live: true,
    status: "LIVE",
    age: "1h ago",
    generatedLabel: "May 11",
  } as FilteredSignal;
}

const candles = [makeCandle(2026, 5, 9), makeCandle(2026, 5, 10), makeCandle(2026, 5, 11)];

describe("buildSignalMarker", () => {
  it("BUY returns arrowUp marker on last candle with symbol text", () => {
    const marker = buildSignalMarker(makeSignal("BUY", "TSLA"), candles);
    expect(marker).not.toBeNull();
    expect(marker!.shape).toBe("arrowUp");
    expect(marker!.position).toBe("belowBar");
    expect(marker!.text).toBe("TSLA");
    expect(marker!.time).toEqual({ year: 2026, month: 5, day: 11 });
  });

  it("SELL returns arrowDown marker", () => {
    const marker = buildSignalMarker(makeSignal("SELL"), candles);
    expect(marker).not.toBeNull();
    expect(marker!.shape).toBe("arrowDown");
    expect(marker!.position).toBe("aboveBar");
  });

  it("HOLD returns null", () => {
    expect(buildSignalMarker(makeSignal("HOLD"), candles)).toBeNull();
  });

  it("null signal returns null", () => {
    expect(buildSignalMarker(null, candles)).toBeNull();
  });

  it("empty candles returns null", () => {
    expect(buildSignalMarker(makeSignal("BUY"), [])).toBeNull();
  });
});
