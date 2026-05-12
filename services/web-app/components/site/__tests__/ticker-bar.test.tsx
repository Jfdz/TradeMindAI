import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

import { TickerBar } from "../ticker-bar";

const mockPrice = (ticker: string, open: number, close: number, date = "2026-05-09") => ({
  ticker,
  date,
  timeFrame: "DAILY",
  ohlcv: { open, high: close, low: open, close, volume: 1000 },
});

describe("TickerBar", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    process.env.MARKET_DATA_INTERNAL_SECRET = "test-secret";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.MARKET_DATA_INTERNAL_SECRET;
  });

  it("renders live prices from backend response", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        prices: [
          mockPrice("AAPL", 170.0, 172.55),
          mockPrice("TSLA", 200.0, 195.0),
          mockPrice("BTC-USD", 60000, 62500),
        ],
      }),
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("AAPL");
    expect(html).toContain("172.55");
    expect(html).toContain("TSLA");
    expect(html).toContain("-2.50%");
  });

  it("maps BTC-USD to BTC/USD display label", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        prices: [mockPrice("BTC-USD", 60000, 62500)],
      }),
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("BTC/USD");
    expect(html).not.toContain("BTC-USD");
  });

  it("maps ETH-USD to ETH/USD display label", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        prices: [mockPrice("ETH-USD", 3000, 3100)],
      }),
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("ETH/USD");
    expect(html).not.toContain("ETH-USD");
  });

  it("renders as-of date in tooltip", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        prices: [mockPrice("NVDA", 800, 820, "2026-05-09")],
      }),
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("as of 2026-05-09");
  });

  it("shows unavailable pill when fetch rejects", async () => {
    vi.mocked(fetch).mockRejectedValue(new Error("network error"));

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("temporarily unavailable");
  });

  it("shows unavailable pill when backend returns non-OK", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 503,
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("temporarily unavailable");
  });

  it("shows unavailable pill when prices array is empty", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({ prices: [] }),
    } as Response);

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("temporarily unavailable");
  });

  it("sends X-Internal-Secret header on the upstream fetch", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({ prices: [mockPrice("AAPL", 170, 172)] }),
    } as Response);

    await TickerBar();

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/prices/latest"),
      expect.objectContaining({
        headers: expect.objectContaining({ "X-Internal-Secret": "test-secret" }),
      }),
    );
  });

  it("shows unavailable pill and skips fetch when MARKET_DATA_INTERNAL_SECRET is missing", async () => {
    delete process.env.MARKET_DATA_INTERNAL_SECRET;

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).toContain("temporarily unavailable");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("does not fall back to static placeholder prices on failure", async () => {
    vi.mocked(fetch).mockRejectedValue(new Error("offline"));

    const node = await TickerBar();
    const html = renderToStaticMarkup(node);

    expect(html).not.toContain("68,412");
    expect(html).not.toContain("187.80");
    expect(html).not.toContain("178.50");
  });
});
