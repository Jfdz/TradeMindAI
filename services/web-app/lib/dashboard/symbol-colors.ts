export const SYMBOL_PALETTE = [
  "#e8b84b",
  "#60a5fa",
  "#00d68f",
  "#ff4d6a",
  "#c084fc",
  "#f59e0b",
] as const;

export function getSymbolColor(symbol: string): string {
  let hash = 0;
  for (let i = 0; i < symbol.length; i++) {
    hash = (hash * 31 + symbol.charCodeAt(i)) | 0;
  }
  const index = Math.abs(hash) % SYMBOL_PALETTE.length;
  return SYMBOL_PALETTE[index];
}