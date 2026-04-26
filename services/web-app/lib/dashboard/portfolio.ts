export type PortfolioPosition = {
  symbol: string;
  name: string;
  shares: number;
  averageCost: number;
  lastPrice: number;
  sector: string;
  color: string;
  trend: number[];
};

export const portfolioPositions: PortfolioPosition[] = [
  {
    symbol: "AAPL",
    name: "Apple",
    shares: 85,
    averageCost: 168.2,
    lastPrice: 178.5,
    sector: "Consumer tech",
    color: "#e8b84b",
    trend: [166, 167, 168, 171, 170, 173, 176, 178, 177, 179],
  },
  {
    symbol: "MSFT",
    name: "Microsoft",
    shares: 46,
    averageCost: 398.6,
    lastPrice: 421.1,
    sector: "Enterprise software",
    color: "#60a5fa",
    trend: [401, 400, 403, 408, 406, 411, 415, 418, 420, 421],
  },
  {
    symbol: "NVDA",
    name: "NVIDIA",
    shares: 22,
    averageCost: 801.4,
    lastPrice: 846.2,
    sector: "Semiconductors",
    color: "#00d68f",
    trend: [805, 808, 810, 818, 823, 829, 834, 838, 842, 846],
  },
  {
    symbol: "AMD",
    name: "AMD",
    shares: 70,
    averageCost: 159.3,
    lastPrice: 171.25,
    sector: "Chips",
    color: "#ff4d6a",
    trend: [158, 159, 161, 163, 165, 167, 168, 170, 171, 171],
  },
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
