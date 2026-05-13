import { describe, expect, it, vi } from "vitest";

function DynamicPlaceholder() {
  return null;
}

vi.mock("next/dynamic", () => ({
  default: () => DynamicPlaceholder,
}));

describe("TradingViewAttribution", () => {
  it("exports the component", async () => {
    const { TradingViewAttribution } = await import("../tradingview-attribution");

    expect(TradingViewAttribution).toBeDefined();
    expect(typeof TradingViewAttribution).toBe("function");
  });
});

describe("TradingViewAdvancedChart (SSR — renders null via dynamic)", () => {
  it("imports without error", async () => {
    const mod = await import("../tradingview-advanced-chart");

    expect(mod.TradingViewAdvancedChart).toBeDefined();
  });
});

describe("TradingViewMiniChart (SSR — useEffect is no-op)", () => {
  it("imports without error", async () => {
    const mod = await import("../tradingview-mini-chart");

    expect(mod.TradingViewMiniChart).toBeDefined();
  });
});
