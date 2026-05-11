import { describe, expect, it, vi } from "vitest";

// Stable plain function so mockReset:true doesn't clear it between tests.
// useState(initial) → [initial, noop]; errored starts false.
vi.mock("react", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react")>();
  return { ...actual, useState: (initial: unknown) => [initial, () => {}] };
});

vi.mock("next/image", () => ({
  default: ({ src, alt, onError }: { src: string; alt: string; onError?: () => void }) => ({
    type: "img",
    props: { src, alt, onError },
  }),
}));

describe("StockLogo", () => {
  it("exports the component", async () => {
    const { StockLogo } = await import("../stock-logo");
    expect(typeof StockLogo).toBe("function");
  });

  it("renders initials span when logoUrl is absent", async () => {
    const { StockLogo } = await import("../stock-logo");
    const result = StockLogo({ ticker: "NVDA" }) as any;
    expect(result.type).toBe("span");
    expect(result.props.children).toBe("NV");
  });

  it("renders initials span when logoUrl is null", async () => {
    const { StockLogo } = await import("../stock-logo");
    const result = StockLogo({ ticker: "AAPL", logoUrl: null }) as any;
    expect(result.type).toBe("span");
    expect(result.props.children).toBe("AA");
  });

  it("renders initials span when logoUrl is empty string", async () => {
    const { StockLogo } = await import("../stock-logo");
    const result = StockLogo({ ticker: "MSFT", logoUrl: "" }) as any;
    expect(result.type).toBe("span");
    expect(result.props.children).toBe("MS");
  });

  it("uses symbol over ticker for initials", async () => {
    const { StockLogo } = await import("../stock-logo");
    const result = StockLogo({ ticker: "BTC-USD", symbol: "BTC", logoUrl: null }) as any;
    expect(result.props.children).toBe("BT");
  });

  it("renders Image with onError handler when logoUrl is present", async () => {
    const { StockLogo } = await import("../stock-logo");
    // With errored=false (initial state) and a logoUrl, should render the Image path
    const result = StockLogo({ ticker: "AAPL", logoUrl: "https://static2.finnhub.io/aapl.png" }) as any;
    expect(result.type).not.toBe("span");
    // onError callback is the mechanism that triggers the initials fallback
    expect(typeof result.props.onError).toBe("function");
  });
});
