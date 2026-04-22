export type PortfolioPosition = {
  symbol: string;
  shares: number;
  averageCost: number;
  lastPrice: number;
  sector: string;
  color: string;
};

export const portfolioPositions: PortfolioPosition[] = [
  { symbol: "AAPL", shares: 85, averageCost: 168.2, lastPrice: 178.5, sector: "Consumer tech", color: "#facc15" },
  { symbol: "MSFT", shares: 46, averageCost: 398.6, lastPrice: 421.1, sector: "Enterprise software", color: "#60a5fa" },
  { symbol: "NVDA", shares: 22, averageCost: 801.4, lastPrice: 846.2, sector: "Semiconductors", color: "#34d399" },
  { symbol: "AMD", shares: 70, averageCost: 159.3, lastPrice: 171.25, sector: "Chips", color: "#fb7185" },
];

export const realizedPnl = 4280;

export function calculatePositionValue(position: PortfolioPosition) {
  return position.shares * position.lastPrice;
}

export function calculateCostBasis(position: PortfolioPosition) {
  return position.shares * position.averageCost;
}

export function calculateUnrealizedPnl(position: PortfolioPosition) {
  return calculatePositionValue(position) - calculateCostBasis(position);
}

export function calculateAllocation(position: PortfolioPosition, portfolioValue: number) {
  if (portfolioValue <= 0) {
    return 0;
  }

  return (calculatePositionValue(position) / portfolioValue) * 100;
}

export function formatMoney(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

export function formatSignedMoney(value: number) {
  const formatted = formatMoney(Math.abs(value));
  return value >= 0 ? `+${formatted}` : `-${formatted}`;
}
