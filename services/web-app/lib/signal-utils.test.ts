import { describe, expect, it } from "vitest";
import type { SignalResponse } from "./api-client";
import { buildSignalReasoning, calculateExpectedMovePct, resolveExpectedMovePct } from "./signal-utils";

describe("calculateExpectedMovePct", () => {
  it("BUY: 130.05 → 135.25 ≈ 4.00%", () => {
    expect(calculateExpectedMovePct(130.05, 135.25)).toBeCloseTo(3.998, 2);
  });

  it("SELL: 100 → 95 = 5.00%", () => {
    expect(calculateExpectedMovePct(100, 95)).toBeCloseTo(5.0, 5);
  });

  it("returns null when entry is null", () => {
    expect(calculateExpectedMovePct(null, 135)).toBeNull();
  });

  it("returns null when target is null", () => {
    expect(calculateExpectedMovePct(130, null)).toBeNull();
  });

  it("returns null when entry is 0", () => {
    expect(calculateExpectedMovePct(0, 135)).toBeNull();
  });

  it("returns null when entry is NaN", () => {
    expect(calculateExpectedMovePct(NaN, 135)).toBeNull();
  });
});

describe("resolveExpectedMovePct", () => {
  it("returns calculated move when entry and target present", () => {
    expect(resolveExpectedMovePct(100, 105, 2)).toBeCloseTo(5.0, 5);
  });

  it("falls back to predictedChangePct when entry is missing", () => {
    expect(resolveExpectedMovePct(null, 105, 3.5)).toBe(3.5);
  });

  it("falls back to predictedChangePct when target is missing", () => {
    expect(resolveExpectedMovePct(100, null, 3.5)).toBe(3.5);
  });

  it("returns null when all inputs are null", () => {
    expect(resolveExpectedMovePct(null, null, null)).toBeNull();
  });
});

function makeSignal(overrides: Partial<SignalResponse> = {}): SignalResponse {
  return {
    id: "sig-1",
    symbol: "AAPL",
    type: "BUY",
    confidence: 0.85,
    generatedAt: "2026-04-17T10:00:00Z",
    timeframe: "DAILY",
    predictedChangePct: 1.5,
    ...overrides,
  };
}

describe("buildSignalReasoning", () => {
  it("returns persisted reasoning when status is READY", () => {
    const signal = makeSignal({
      reasoning: "AAPL bullish breakout detected with 85% confidence.",
      reasoningStatus: "READY",
    });

    expect(buildSignalReasoning(signal, 182.5)).toBe(
      "AAPL bullish breakout detected with 85% confidence."
    );
  });

  it("returns persisted reasoning when status is FALLBACK", () => {
    const signal = makeSignal({
      reasoning: "AAPL bullish breakout detected with 85% confidence.",
      reasoningStatus: "FALLBACK",
    });

    expect(buildSignalReasoning(signal, 182.5)).toBe(
      "AAPL bullish breakout detected with 85% confidence."
    );
  });

  it("falls back to formula when status is PENDING", () => {
    const signal = makeSignal({
      reasoning: "should not appear",
      reasoningStatus: "PENDING",
    });

    const result = buildSignalReasoning(signal, 182.5);

    expect(result).not.toContain("should not appear");
    expect(result).toContain("$182.50");
    expect(result).toContain("1.5%");
  });

  it("falls back to formula when reasoningStatus is absent", () => {
    const signal = makeSignal({ type: "SELL", predictedChangePct: -2.0 });

    const result = buildSignalReasoning(signal, 200);

    expect(result).toContain("$200.00");
    expect(result).toContain("2.0%");
    expect(result).toContain("Bearish");
  });

  it("uses 'the latest market price' when latestPrice is null and status is PENDING", () => {
    const signal = makeSignal({ reasoningStatus: "PENDING" });

    const result = buildSignalReasoning(signal, null);

    expect(result).toContain("the latest market price");
  });

  it("returns neutral phrasing for HOLD signals", () => {
    const signal = makeSignal({ type: "HOLD", reasoningStatus: "PENDING" });

    const result = buildSignalReasoning(signal, 100);

    expect(result).toContain("Neutral");
  });
});
