import type { BusinessDay, SeriesMarker, Time } from "lightweight-charts";

import type {
  MarketPriceResponse,
  NotificationPreferencesResponse,
  PagedResponse,
  PortfolioHoldingResponse,
  PortfolioOverviewResponse,
  SignalResponse,
  UserProfileResponse,
} from "@/lib/api-client";
import { convertPricesToCandles } from "@/lib/dashboard/signal-derivation";

export type FilteredSignal = SignalResponse & {
  latestPrice: number | null;
  entry: number | null;
  takeProfit: number | null;
  stopLoss: number | null;
  expectedMovePct: number | null;
  live: boolean;
  status: "NEW" | "LIVE" | "ACTIVE";
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

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8082";

export async function fetchCandles(ticker: string, token?: string): Promise<DashboardCandle[]> {
  const from = new Date();
  from.setUTCDate(from.getUTCDate() - 8);
  const params = new URLSearchParams({
    timeframe: "DAILY",
    from: from.toISOString().slice(0, 10),
    to: new Date().toISOString().slice(0, 10),
    size: "24",
  });
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/prices/${encodeURIComponent(ticker)}/history?${params.toString()}`, {
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    const data = (await res.json()) as PagedResponse<MarketPriceResponse>;
    return convertPricesToCandles(data.content ?? []);
  } catch {
    return [];
  }
}

export function buildHoldingTrend(prices: MarketPriceResponse[]) {
  if (prices.length === 0) {
    return [];
  }

  return prices
    .slice()
    .sort((left, right) => new Date(left.date).getTime() - new Date(right.date).getTime())
    .map((price) => price.adjustedClose ?? price.ohlcv.close);
}
