import { describe, expect, it } from "vitest";
import {
  buildSignalMarker,
  computePeakPrice,
  deriveSignal,
  hasValidReasoningNews,
  pickPeakColor,
} from "../signal-derivation";
import type { DashboardCandle, FilteredSignal } from "../dashboard-api";
import type { SignalResponse } from "@/lib/api-client";

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
    expectedMovePct: 5,
    live: true,
    status: "LIVE",
    age: "1h ago",
    generatedLabel: "May 11",
  };
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

function makeApiSignal(overrides: Partial<SignalResponse> = {}): SignalResponse {
  return {
    id: "sig-1",
    symbol: "AAPL",
    type: "BUY",
    timeframe: "1D",
    confidence: 0.8,
    entryPrice: 100,
    takeProfitPct: 5,
    stopLossPct: 3,
    predictedChangePct: 4,
    reasoning: "test",
    reasoningStatus: "READY",
    generatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function makeReasoningNews(overrides: Record<string, unknown> = {}) {
  return {
    headline: "Apple Stock Surges",
    url: "https://example.com/news",
    imageUrl: "https://example.com/image.jpg",
    source: "Example News",
    publishedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe("deriveSignal", () => {
  it("prefers backend-provided targetPrice and stopLoss over frontend math", () => {
    const signal = makeApiSignal({ targetPrice: 110.5, stopLoss: 98.25 });
    const derived = deriveSignal(signal, 100);
    expect(derived.takeProfit).toBe(110.5);
    expect(derived.stopLoss).toBe(98.25);
  });

  it("falls back to frontend math when backend fields are absent", () => {
    const signal = makeApiSignal();
    const derived = deriveSignal(signal, 100);
    // 100 * (1 + 5/100) = 105; 100 * (1 - 3/100) = 97
    expect(derived.takeProfit).toBe(105);
    expect(derived.stopLoss).toBe(97);
  });

  it("computes expectedMovePct from entry and takeProfit when both present", () => {
    const signal = makeApiSignal({ entryPrice: 130.05, targetPrice: 135.25 });
    const derived = deriveSignal(signal, null);
    // |(135.25 - 130.05) / 130.05| * 100 ≈ 3.998
    expect(derived.expectedMovePct).toBeCloseTo(3.998, 2);
  });

  it("falls back to predictedChangePct for expectedMovePct when prices absent", () => {
    const signal = makeApiSignal({ entryPrice: null, targetPrice: undefined });
    const derived = deriveSignal(signal, null);
    expect(derived.expectedMovePct).toBe(4); // predictedChangePct from makeApiSignal
  });
});

describe("hasValidReasoningNews", () => {
  it("returns true when reasoning news has a valid article image", () => {
    const signal = makeApiSignal({ reasoningNews: makeReasoningNews() });
    expect(hasValidReasoningNews(signal)).toBe(true);
  });

  it("returns false when reasoning news is null", () => {
    const signal = makeApiSignal({ reasoningNews: null });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when reasoning news is undefined", () => {
    const signal = makeApiSignal({ reasoningNews: undefined });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when reasoning news has null imageUrl", () => {
    const signal = makeApiSignal({ reasoningNews: makeReasoningNews({ imageUrl: null }) });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when reasoning news has empty imageUrl", () => {
    const signal = makeApiSignal({ reasoningNews: makeReasoningNews({ imageUrl: "" }) });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when reasoning news has whitespace-only imageUrl", () => {
    const signal = makeApiSignal({ reasoningNews: makeReasoningNews({ imageUrl: "   " }) });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when reasoning news has Yahoo template image", () => {
    const signal = makeApiSignal({
      reasoningNews: makeReasoningNews({
        imageUrl: "https://s.yimg.com/rz/stage/p/yahoo_finance_en-US_h_p_finance_2.png",
        source: "Yahoo Finance",
      }),
    });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });

  it("returns false when imageUrl is not an http URL", () => {
    const signal = makeApiSignal({
      reasoningNews: makeReasoningNews({ imageUrl: "data:image/png;base64,iVBORw0KGgo=" }),
    });
    expect(hasValidReasoningNews(signal)).toBe(false);
  });
});

describe("computePeakPrice", () => {
  it("BUY peak is entry scaled up by the favorable fraction", () => {
    expect(computePeakPrice("BUY", 100, 0.05)).toBeCloseTo(105);
  });

  it("SELL trough is entry scaled down by the favorable fraction", () => {
    expect(computePeakPrice("SELL", 100, 0.05)).toBeCloseTo(95);
  });

  it("handles a negative maxProfit (price never moved favorably)", () => {
    // BUY that never rose: peak sits below entry.
    expect(computePeakPrice("BUY", 100, -0.02)).toBeCloseTo(98);
    // SELL that never fell: trough sits above entry.
    expect(computePeakPrice("SELL", 100, -0.02)).toBeCloseTo(102);
  });

  it("returns null for HOLD", () => {
    expect(computePeakPrice("HOLD", 100, 0.05)).toBeNull();
  });

  it("returns null when entryPrice or maxProfit is missing", () => {
    expect(computePeakPrice("BUY", null, 0.05)).toBeNull();
    expect(computePeakPrice("BUY", 100, null)).toBeNull();
    expect(computePeakPrice("BUY", undefined, undefined)).toBeNull();
  });
});

describe("pickPeakColor", () => {
  it("BUY is green when the peak reaches the target, red otherwise", () => {
    expect(pickPeakColor("BUY", 110, 105)).toBe("text-green");
    expect(pickPeakColor("BUY", 105, 105)).toBe("text-green"); // equality counts
    expect(pickPeakColor("BUY", 104, 105)).toBe("text-red");
  });

  it("SELL is green when the trough drops to the target, red otherwise", () => {
    expect(pickPeakColor("SELL", 95, 95)).toBe("text-green"); // equality counts
    expect(pickPeakColor("SELL", 94, 95)).toBe("text-green");
    expect(pickPeakColor("SELL", 96, 95)).toBe("text-red");
  });

  it("is neutral for HOLD and for missing inputs", () => {
    expect(pickPeakColor("HOLD", 110, 105)).toBe("text-text-1");
    expect(pickPeakColor("BUY", null, 105)).toBe("text-text-1");
    expect(pickPeakColor("BUY", 110, null)).toBe("text-text-1");
  });
});
