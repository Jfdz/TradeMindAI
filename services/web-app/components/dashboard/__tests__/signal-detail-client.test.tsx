import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { SignalResponse } from "@/lib/api-client";

vi.mock("next/link", () => ({
  default: ({ href, children, className }: { href: string; children: React.ReactNode; className?: string }) =>
    React.createElement("a", { href, className }, children),
}));

vi.mock("@/components/ui/button", () => ({
  Button: ({ children, asChild, ...rest }: { children: React.ReactNode; asChild?: boolean; [k: string]: unknown }) =>
    React.createElement(asChild ? React.Fragment : "button", asChild ? {} : rest, children),
}));

vi.mock("@/components/dashboard/signal-chart", () => ({
  SignalChart: () => React.createElement("div", { "data-testid": "signal-chart" }),
}));

vi.mock("@/components/site/icons", () => ({
  ArrowRightIcon: () => React.createElement("span"),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn(),
}));

import { useQuery } from "@tanstack/react-query";
import { SignalDetailClient } from "../signal-detail-client";

function makeSignal(overrides: Partial<SignalResponse> = {}): SignalResponse {
  return {
    id: "sig-1",
    symbol: "PYPL",
    type: "BUY",
    timeframe: "1D",
    confidence: 0.82,
    entryPrice: null,
    takeProfitPct: 5,
    stopLossPct: 3,
    predictedChangePct: 4.5,
    reasoning: "Strong momentum.",
    reasoningStatus: "READY",
    generatedAt: new Date().toISOString(),
    ...overrides,
  } as SignalResponse;
}

describe("SignalDetailClient", () => {
  it("null-entry path: renders em-dash with legacy tooltip, no throw", () => {
    (useQuery as ReturnType<typeof vi.fn>).mockReturnValue({
      data: { signal: makeSignal({ entryPrice: null }), latestPrice: null, candles: [] },
      isLoading: false,
      error: null,
    });

    const html = renderToStaticMarkup(React.createElement(SignalDetailClient, { signalId: "sig-1" }));
    expect(html).toContain("—");
    expect(html).toContain("Entry price not captured for legacy signals");
  });

  it("derived-entry path: shows entry 152.30 from latestPrice when entryPrice is null", () => {
    (useQuery as ReturnType<typeof vi.fn>).mockReturnValue({
      data: { signal: makeSignal({ entryPrice: null }), latestPrice: 152.30, candles: [] },
      isLoading: false,
      error: null,
    });

    const html = renderToStaticMarkup(React.createElement(SignalDetailClient, { signalId: "sig-1" }));
    expect(html).toContain("152.30");
  });
});
