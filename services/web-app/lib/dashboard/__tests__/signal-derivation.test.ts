import { describe, expect, it } from "vitest";
import { buildSignalMarker, deriveSignal, hasValidReasoningNews } from "../signal-derivation";
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
