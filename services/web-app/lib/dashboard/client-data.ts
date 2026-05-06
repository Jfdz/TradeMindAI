import {
  apiClient,
  type BacktestJobResponse,
  type MarketPriceResponse,
  type MarketSymbolResponse,
  type SignalResponse,
} from "@/lib/api-client";
import {
  buildHoldingTrend,
  type DashboardPageData,
  type PortfolioPageData,
  type SettingsPageData,
  type SignalDetailData,
  type FilteredSignal,
} from "@/lib/dashboard/dashboard-api";
import { convertPricesToCandles, deriveSignal } from "@/lib/dashboard/signal-derivation";
import { assignSymbolColors } from "@/lib/dashboard/symbol-colors";

export async function fetchSettingsPageData(): Promise<SettingsPageData> {
  const [profile, preferences] = await Promise.all([
    apiClient.getCurrentUser(),
    apiClient.getNotificationPreferences(),
  ]);

  return { profile, preferences };
}

export async function fetchSignalsPageData(): Promise<FilteredSignal[]> {
  const response = await apiClient.getSignals();
  const content = response.content ?? [];
  const uniqueSymbols = Array.from(new Set(content.map((signal) => signal.symbol)));
  const latestPrices = await apiClient.getLatestPrices(uniqueSymbols);
  const latestPriceBySymbol = new Map(
    latestPrices.prices.map((price) => [price.ticker, price.adjustedClose ?? price.ohlcv.close] as const)
  );

  return content.map((signal) => deriveSignal(signal, latestPriceBySymbol.get(signal.symbol) ?? null));
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
  const [portfolio, symbolResponse] = await Promise.all([apiClient.getPortfolio(), apiClient.getSymbols()]);
  const symbolMap = new Map<string, MarketSymbolResponse>(symbolResponse.content.map((symbol) => [symbol.ticker, symbol]));

  const colorMap = assignSymbolColors(portfolio.holdings.map((h) => h.symbol));

  const holdingSymbols = portfolio.holdings.map((h) => h.symbol);
  const from = new Date();
  from.setUTCDate(from.getUTCDate() - 7);
  const historyBatch = await apiClient.getHistoricalPricesBatch(
    holdingSymbols,
    from.toISOString().slice(0, 10),
    new Date().toISOString().slice(0, 10),
    8
  );

  const holdings = portfolio.holdings.map((holding) => {
    const symbol = symbolMap.get(holding.symbol);
    return {
      ...holding,
      name: symbol?.name ?? holding.symbol,
      sector: symbol?.sector ?? "Portfolio holding",
      color: colorMap.get(holding.symbol)!,
      trend: buildHoldingTrend(historyBatch[holding.symbol] ?? []),
    };
  });

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
