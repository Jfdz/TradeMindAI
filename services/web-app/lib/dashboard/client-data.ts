import {
  apiClient,
  type BacktestJobResponse,
} from "@/lib/api-client";
import {
  type DashboardPageData,
  type PortfolioPageData,
  type SettingsPageData,
  type SignalDetailData,
  type FilteredSignal,
} from "@/lib/dashboard/dashboard-api";
import { convertPricesToCandles, deriveSignal } from "@/lib/dashboard/signal-derivation";
import { assignSymbolColors } from "@/lib/dashboard/symbol-colors";

export type SignalsPageInfo = {
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
};

export async function fetchSettingsPageData(): Promise<SettingsPageData> {
  const [profile, preferences] = await Promise.all([
    apiClient.getCurrentUser(),
    apiClient.getNotificationPreferences(),
  ]);

  return { profile, preferences };
}

export async function fetchSignalsPageData(opts?: {
  page?: number;
  size?: number;
}): Promise<{ items: FilteredSignal[]; pageInfo: SignalsPageInfo }> {
  const response = await apiClient.getSignals(opts);
  const content = response.content ?? [];
  const uniqueSymbols = Array.from(new Set(content.map((signal) => signal.symbol)));
  const latestPrices = await apiClient.getLatestPrices(uniqueSymbols);
  const latestPriceBySymbol = new Map(
    latestPrices.prices.map((price) => [price.ticker, price.adjustedClose ?? price.ohlcv.close] as const)
  );

  const items = content.map((signal) => deriveSignal(signal, latestPriceBySymbol.get(signal.symbol) ?? null));

  return {
    items,
    pageInfo: {
      pageNumber: response.number ?? 0,
      pageSize: response.size ?? 10,
      totalElements: response.totalElements ?? 0,
      totalPages: response.totalPages ?? 1,
      isFirst: response.first ?? true,
      isLast: response.last ?? true,
    },
  };
}

export async function fetchSignalDetailData(signalId: string): Promise<SignalDetailData> {
  const signal = await apiClient.getSignal(signalId);
  const latest = await apiClient.getLatestPrice(signal.symbol);
  const latestClose = latest?.adjustedClose ?? latest?.ohlcv.close ?? null;
  const from = new Date(signal.generatedAt);
  from.setUTCDate(from.getUTCDate() - 10);
  const historical = await apiClient.getHistoricalPrices(
    signal.symbol,
    from.toISOString().slice(0, 10),
    new Date().toISOString().slice(0, 10),
    18
  );

  return {
    signal,
    latestPrice: latestClose,
    candles: convertPricesToCandles(historical.content),
  };
}

export async function fetchPortfolioPageData(): Promise<PortfolioPageData> {
  const portfolio = await apiClient.getPortfolio();
  const colorMap = assignSymbolColors(portfolio.holdings.map((h) => h.symbol));

  const holdings = portfolio.holdings.map((holding) => ({
    ...holding,
    name: holding.name ?? holding.symbol,
    sector: holding.sector ?? "Portfolio holding",
    color: colorMap.get(holding.symbol)!,
    trend: holding.trend7d ?? [],
  }));

  return { portfolio, holdings };
}

export async function fetchDashboardPageData(): Promise<DashboardPageData> {
  const response = await fetch("/api/dashboard", {
    method: "GET",
    headers: {
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return (await response.json()) as DashboardPageData;
}

export async function fetchBacktest(backtestId: string): Promise<BacktestJobResponse> {
  return apiClient.getBacktest(backtestId);
}
