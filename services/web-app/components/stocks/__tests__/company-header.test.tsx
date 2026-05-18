import { describe, expect, it, vi } from "vitest";

vi.mock("next/image", () => ({
  default: ({ src, alt }: { src: string; alt: string }) => ({ src, alt }),
}));

describe("CompanyHeader", () => {
  it("exports the component", async () => {
    const { CompanyHeader } = await import("../company-header");

    expect(typeof CompanyHeader).toBe("function");
  });

  it("renders with a full profile without throwing", async () => {
    const { CompanyHeader } = await import("../company-header");

    const profile = {
      ticker: "AAPL",
      name: "Apple Inc.",
      logo: "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/AAPL.png",
      country: "US",
      currency: "USD",
      exchange: "NASDAQ",
      ipo: "1980-12-12",
      marketCap: 3_000_000_000_000,
      phone: null,
      weburl: "https://www.apple.com/",
      industry: "Technology",
    };

    expect(() => CompanyHeader({ profile, ticker: "AAPL" })).not.toThrow();
  });

  it("renders placeholder when profile is null", async () => {
    const { CompanyHeader } = await import("../company-header");

    expect(() => CompanyHeader({ profile: null, ticker: "BTC-USD" })).not.toThrow();
  });
});
