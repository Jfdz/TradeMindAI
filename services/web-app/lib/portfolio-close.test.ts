import { describe, expect, it } from "vitest";

import type { PortfolioHoldingResponse } from "@/lib/api-client";
import { buildClosePositionPayload, calculateClosePositionPnl } from "@/lib/portfolio-close";

const holding: PortfolioHoldingResponse = {
  id: "position-1",
  symbol: "AAPL",
  quantity: 2,
  averageCost: 100,
  lastPrice: 110,
  marketValue: 220,
  unrealizedPnl: 20,
  allocationPct: 100,
  status: "OPEN",
  openedAt: "2026-04-16T10:00:00Z",
  closedAt: null,
};

describe("buildClosePositionPayload", () => {
  it("requires exit price", () => {
    expect(() =>
      buildClosePositionPayload({
        exitPrice: "",
        fees: "",
        closedAt: "",
      })
    ).toThrow("Exit price is required.");
  });

  it("builds payload with optional fees and timestamp", () => {
    const payload = buildClosePositionPayload({
      exitPrice: "175.50",
      fees: "1.25",
      closedAt: "2026-04-20T10:00",
    });

    expect(payload).toMatchObject({
      exitPrice: 175.5,
      fees: 1.25,
    });
    expect(new Date(payload.closedAt ?? "").getTime()).toBe(new Date("2026-04-20T10:00").getTime());
  });
});

describe("calculateClosePositionPnl", () => {
  it("computes realized pnl using exit price, quantity, and fees", () => {
    expect(
      calculateClosePositionPnl(holding, {
        exitPrice: 110,
        fees: 3,
      })
    ).toBe(17);
  });
});
