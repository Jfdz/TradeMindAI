import { describe, expect, it } from "vitest";
import type { SignalResponse } from "./api-client";
import { buildSignalReasoning } from "./signal-utils";

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
