export const SYMBOL_PALETTE = [
  "#e8b84b",
  "#60a5fa",
  "#00d68f",
  "#ff4d6a",
  "#c084fc",
  "#f59e0b",
] as const;

const GOLDEN_ANGLE = 137.508;
const SATURATION = 70;
const LIGHTNESS = 55;

function synthesizeColor(index: number): string {
  const hue = (index * GOLDEN_ANGLE) % 360;
  return `hsl(${hue}, ${SATURATION}%, ${LIGHTNESS}%)`;
}

export function assignSymbolColors(symbols: string[]): Map<string, string> {
  const colorMap = new Map<string, string>();
  const uniqueSymbols = [...new Set(symbols)].sort((a, b) => a.localeCompare(b));

  for (let i = 0; i < uniqueSymbols.length; i++) {
    if (i < SYMBOL_PALETTE.length) {
      colorMap.set(uniqueSymbols[i], SYMBOL_PALETTE[i]);
    } else {
      colorMap.set(uniqueSymbols[i], synthesizeColor(i));
    }
  }

  return colorMap;
}

export function getSymbolColor(symbol: string): string {
  let hash = 0;
  for (let i = 0; i < symbol.length; i++) {
    const code = symbol.codePointAt(i) ?? 0;
    hash = Math.trunc(hash * 31 + code);
  }
  const index = Math.abs(hash) % SYMBOL_PALETTE.length;
  return SYMBOL_PALETTE[index];
}