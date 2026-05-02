import { describe, expect, it } from "vitest";
import { getSymbolColor, SYMBOL_PALETTE } from "@/lib/dashboard/symbol-colors";

describe("getSymbolColor", () => {
  it("returns a palette color", () => {
    expect(SYMBOL_PALETTE).toContain(getSymbolColor("PYPL"));
  });

  it("is deterministic for the same symbol", () => {
    expect(getSymbolColor("PYPL")).toBe(getSymbolColor("PYPL"));
    expect(getSymbolColor("AAPL")).toBe(getSymbolColor("AAPL"));
  });

  it("handles empty string without throwing", () => {
    expect(SYMBOL_PALETTE).toContain(getSymbolColor(""));
  });
});