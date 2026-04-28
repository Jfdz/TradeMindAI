import type { BusinessDay, SeriesMarker, Time } from "lightweight-charts";

import type {
  MarketPriceResponse,
  NotificationPreferencesResponse,
  PortfolioHoldingResponse,
  PortfolioOverviewResponse,
  SignalResponse,
  UserProfileResponse,
} from "@/lib/api-client";

export type FilteredSignal = SignalResponse & {
  latestPrice: number | null;
  entry: number | null;
  takeProfit: number | null;
  stopLoss: number | null;
  live: boolean;
  status: "LIVE" | "PENDING";
  age: string;
  generatedLabel: string;
  reasoning: string;
};

export type EnrichedHolding = PortfolioHoldingResponse & {
  name: string;
  sector: string;
  color: string;
  trend: number[];
};

export type DashboardCandle = {
  time: BusinessDay;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

export type DashboardPageData = {
  portfolio: PortfolioOverviewResponse;
  signals: FilteredSignal[];
  holdings: EnrichedHolding[];
  chartCandles: DashboardCandle[];
  chartMarker: SeriesMarker<Time> | null;
};

export type SettingsPageData = {
  profile: UserProfileResponse;
  preferences: NotificationPreferencesResponse;
};

export type SignalDetailData = {
  signal: SignalResponse;
  latestPrice: number | null;
  candles: DashboardCandle[];
};

export type PortfolioPageData = {
  portfolio: PortfolioOverviewResponse;
  holdings: EnrichedHolding[];
};

export function buildHoldingTrend(prices: MarketPriceResponse[], lastPrice: number) {
  if (prices.length === 0) {
    return Array.from({ length: 10 }, (_, index) => Number((lastPrice * (0.96 + index * 0.01)).toFixed(2)));
  }

  return prices
    .slice()
    .sort((left, right) => new Date(left.date).getTime() - new Date(right.date).getTime())
    .map((price) => price.adjustedClose ?? price.ohlcv.close);
}
