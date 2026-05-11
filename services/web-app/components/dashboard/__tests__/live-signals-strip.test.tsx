import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { LiveSignalsStrip } from "../live-signals-strip";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

function makeSignal(id: string, symbol: string, type: "BUY" | "SELL" | "HOLD", live: boolean): FilteredSignal {
  return {
    id,
    symbol,
    type,
    live,
    timeframe: "1D",
    confidence: 0.75,
    entryPrice: null,
    takeProfitPct: 5,
    stopLossPct: 3,
    predictedChangePct: 3,
    reasoning: "Test",
    reasoningStatus: "READY",
    generatedAt: new Date().toISOString(),
    latestPrice: 100,
    entry: 100,
    takeProfit: 105,
    stopLoss: 97,
    status: "LIVE",
    age: "1h ago",
    generatedLabel: "May 11",
  } as FilteredSignal;
}

describe("LiveSignalsStrip", () => {
  it("renders live non-HOLD signals as chips", () => {
    const signals = [
      makeSignal("1", "AAPL", "BUY", true),
      makeSignal("2", "MSFT", "SELL", true),
      makeSignal("3", "TSLA", "HOLD", true),
      makeSignal("4", "NVDA", "BUY", false),
    ];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "1",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toContain("AAPL");
    expect(html).toContain("MSFT");
    expect(html).not.toContain("TSLA");
    expect(html).not.toContain("NVDA");
  });

  it("renders null when no live non-HOLD signals", () => {
    const signals = [makeSignal("1", "AAPL", "HOLD", true)];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "1",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toBe("");
  });
});
