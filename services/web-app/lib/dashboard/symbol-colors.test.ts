import { describe, expect, it } from "vitest";
import { assignSymbolColors, getSymbolColor, SYMBOL_PALETTE } from "@/lib/dashboard/symbol-colors";

describe("assignSymbolColors", () => {
  it("returns unique colors for N=1", () => {
    const colorMap = assignSymbolColors(["AAPL"]);
    const colors = Array.from(colorMap.values());
    expect(colors).toHaveLength(1);
    expect(new Set(colors).size).toBe(1);
  });

  it("returns unique colors for N=6", () => {
    const colorMap = assignSymbolColors(["A", "B", "C", "D", "E", "F"]);
    const colors = Array.from(colorMap.values());
    expect(colors).toHaveLength(6);
    expect(new Set(colors).size).toBe(6);
  });

  it("returns unique colors for N=7 (uses synthesized)", () => {
    const colorMap = assignSymbolColors(["A", "B", "C", "D", "E", "F", "G"]);
    const colors = Array.from(colorMap.values());
    expect(colors).toHaveLength(7);
    expect(new Set(colors).size).toBe(7);
  });

  it("returns unique colors for N=12", () => {
    const colorMap = assignSymbolColors(Array.from({ length: 12 }, (_, i) => `SYM${i}`));
    const colors = Array.from(colorMap.values());
    expect(colors).toHaveLength(12);
    expect(new Set(colors).size).toBe(12);
  });

  it("returns unique colors for N=50", () => {
    const colorMap = assignSymbolColors(Array.from({ length: 50 }, (_, i) => `SYM${i}`));
    const colors = Array.from(colorMap.values());
    expect(colors).toHaveLength(50);
    expect(new Set(colors).size).toBe(50);
  });

  it("is deterministic - same input yields same colors", () => {
    const symbols = ["Z", "A", "M"];
    const map1 = assignSymbolColors(symbols);
    const map2 = assignSymbolColors([...symbols]);
    expect(Array.from(map1.values())).toEqual(Array.from(map2.values()));
  });

  it("prefers palette for N<=6", () => {
    const colorMap = assignSymbolColors(["A", "B", "C"]);
    const colors = Array.from(colorMap.values());
    colors.forEach((color) => {
      expect(SYMBOL_PALETTE).toContain(color);
    });
  });

  it("first 6 use palette colors", () => {
    const colorMap = assignSymbolColors(["A", "B", "C", "D", "E", "F"]);
    const symbols = Array.from(colorMap.keys());
    for (let i = 0; i < 6; i++) {
      expect(SYMBOL_PALETTE).toContain(colorMap.get(symbols[i]));
    }
  });
});

describe("getSymbolColor", () => {
  it("returns a palette color", () => {
    expect(SYMBOL_PALETTE).toContain(getSymbolColor("PYPL"));
  });

  it("is deterministic for the same symbol", () => {
    expect(getSymbolColor("PYPL")).toBe(getSymbolColor("PYPL"));
  });

  it("handles empty string without throwing", () => {
    expect(SYMBOL_PALETTE).toContain(getSymbolColor(""));
  });
});