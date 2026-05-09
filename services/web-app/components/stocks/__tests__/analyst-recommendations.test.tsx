import { describe, expect, it } from "vitest";

describe("AnalystRecommendationsBar", () => {
  it("exports the component", async () => {
    const { AnalystRecommendationsBar } = await import("../analyst-recommendations-bar");

    expect(typeof AnalystRecommendationsBar).toBe("function");
  });

  it("returns null for empty recommendations", async () => {
    const { AnalystRecommendationsBar } = await import("../analyst-recommendations-bar");

    const result = AnalystRecommendationsBar({ recommendations: [] });

    expect(result).toBeNull();
  });

  it("returns null when total analyst count is zero", async () => {
    const { AnalystRecommendationsBar } = await import("../analyst-recommendations-bar");

    const rec = { ticker: "AAPL", period: "2026-05-01", buy: 0, hold: 0, sell: 0, strongBuy: 0, strongSell: 0 };
    const result = AnalystRecommendationsBar({ recommendations: [rec] });

    expect(result).toBeNull();
  });

  it("renders SVG bar when data is present", async () => {
    const { AnalystRecommendationsBar } = await import("../analyst-recommendations-bar");

    const rec = { ticker: "AAPL", period: "2026-05-01", buy: 15, hold: 5, sell: 2, strongBuy: 8, strongSell: 1 };
    const result = AnalystRecommendationsBar({ recommendations: [rec] });

    expect(result).not.toBeNull();
  });
});
