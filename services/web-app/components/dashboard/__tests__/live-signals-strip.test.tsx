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
  };
}

describe("LiveSignalsStrip", () => {
  it("renders trigger with selected signal symbol and type", () => {
    const signals = [
      makeSignal("1", "AAPL", "BUY", true),
      makeSignal("2", "MSFT", "SELL", true),
      makeSignal("3", "TSLA", "HOLD", false),
    ];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "2",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toContain("MSFT");
    expect(html).toContain("SELL");
    expect(html).toContain('aria-haspopup="listbox"');
    expect(html).toContain('aria-expanded="false"');
  });

  it("falls back to first signal when selectedSignalId not found", () => {
    const signals = [
      makeSignal("1", "AAPL", "BUY", true),
      makeSignal("2", "MSFT", "SELL", true),
    ];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "missing",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toContain("AAPL");
  });

  it("shows live count in counter chip", () => {
    const signals = [
      makeSignal("1", "AAPL", "BUY", true),
      makeSignal("2", "MSFT", "SELL", true),
      makeSignal("3", "NVDA", "BUY", true),
    ];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "1",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toContain("3 live");
  });

  it("renders an open-detail link for the active signal, not a cmd-K hint", () => {
    // C2.3 — the ⌘K keyboard-hint pill was replaced with a link to the
    // active signal's stock-detail page.
    const signals = [makeSignal("1", "AAPL", "BUY", true)];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "1",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).not.toContain("⌘K");
    expect(html).toContain("Open detail");
    expect(html).toContain("/dashboard/stocks/AAPL");
  });

  it("renders null when signals array is empty", () => {
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals: [],
        selectedSignalId: "",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).toBe("");
  });

  it("does not render dropdown listbox in initial closed state", () => {
    const signals = [makeSignal("1", "AAPL", "BUY", true)];
    const html = renderToStaticMarkup(
      React.createElement(LiveSignalsStrip, {
        signals,
        selectedSignalId: "1",
        onSignalChange: vi.fn(),
      })
    );
    expect(html).not.toContain('role="listbox"');
    expect(html).not.toContain('role="option"');
  });
});
