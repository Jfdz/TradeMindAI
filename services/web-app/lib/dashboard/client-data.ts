import {
  apiClient,
  type BacktestJobResponse,
  type MarketPriceResponse,
  type MarketSymbolResponse,
  type SignalResponse,
} from "@/lib/api-client";
import {
  buildHoldingTrend,
  type DashboardCandle,
  type DashboardPageData,
  type PortfolioPageData,
  type SettingsPageData,
  type SignalDetailData,
  type FilteredSignal,
} from "@/lib/dashboard/dashboard-api";
import { buildSignalReasoning } from "@/lib/signal-utils";

const palette = ["#e8b84b", "#60a5fa", "#00d68f", "#ff4d6a", "#c084fc", "#f59e0b"];

function formatAge(value: string) {
  const generatedAt = new Date(value).getTime();
  if (Number.isNaN(generatedAt)) {
    return "recently";
  }

  const diffMinutes = Math.max(Math.round((Date.now() - generatedAt) / 60000), 0);
  if (diffMinutes < 60) {
    return `${Math.max(diffMinutes, 1)}m ago`;
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }

  return `${Math.max(Math.round(diffHours / 24), 1)}d ago`;
}

function toBusinessDay(value: string): DashboardCandle["time"] {
  const date = new Date(value);

  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  };
}

export function deriveSignal(signal: SignalResponse, latestPrice: number | null): FilteredSignal {
  const entry = latestPrice;
  const takeProfit =
    signal.type === "BUY"
      ? signal.takeProfitPct != null && entry != null
        ? entry * (1 + signal.takeProfitPct / 100)
        : null
      : signal.type === "SELL"
        ? signal.takeProfitPct != null && entry != null
          ? entry * (1 - signal.takeProfitPct / 100)
          : null
        : entry;
  const stopLoss =
    signal.type === "BUY"
      ? signal.stopLossPct != null && entry != null
        ? entry * (1 - signal.stopLossPct / 100)
        : null
      : signal.type === "SELL"
        ? signal.stopLossPct != null && entry != null
          ? entry * (1 + signal.stopLossPct / 100)
          : null
        : entry;
  const live = Date.now() - new Date(signal.generatedAt).getTime() < 1000 * 60 * 60 * 24;

  return {
    ...signal,
    latestPrice,
    entry,
    takeProfit,
    stopLoss,
    live,
    status: live ? "LIVE" : "PENDING",
    age: formatAge(signal.generatedAt),
    generatedLabel: new Date(signal.generatedAt).toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
    }),
    reasoning: buildSignalReasoning(signal, latestPrice),
  };
}

function synthesizeCandles(basePrice: number, generatedAt: string): DashboardCandle[] {
  return Array.from({ length: 12 }, (_, index) => {
    const drift = (index - 5) * 1.35;
    const open = Number((basePrice - 8 + drift).toFixed(2));
    const close = Number((open + (index % 2 === 0 ? 2.1 : -1.4)).toFixed(2));
    const high = Number((Math.max(open, close) + 2.3).toFixed(2));
    const low = Number((Math.min(open, close) - 1.9).toFixed(2));
    const date = new Date(generatedAt);
    date.setUTCDate(date.getUTCDate() + index - 6);

    return {
      time: toBusinessDay(date.toISOString()),
      open,
      high,
      low,
      close,
      volume: 820000 + index * 94000,
    };
  });
}

function buildSignalCandles(signal: SignalResponse, history: MarketPriceResponse[], fallbackPrice: number): DashboardCandle[] {
  if (history.length > 0) {
    return history
      .slice()
      .reverse()
      .map((bar) => ({
        time: toBusinessDay(bar.date),
        open: bar.ohlcv.open,
        high: bar.ohlcv.high,
        low: bar.ohlcv.low,
        close: bar.ohlcv.close,
        volume: bar.ohlcv.volume,
      }));
  }

  return synthesizeCandles(signal.predictedChangePct ?? fallbackPrice, signal.generatedAt);
}

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
    candles: buildSignalCandles(signal, historical.content, latestClose ?? 0),
  };
}

export async function fetchPortfolioPageData(): Promise<PortfolioPageData> {
  const [portfolio, symbolResponse] = await Promise.all([apiClient.getPortfolio(), apiClient.getSymbols()]);
  const symbolMap = new Map<string, MarketSymbolResponse>(symbolResponse.content.map((symbol) => [symbol.ticker, symbol]));

  const holdings = await Promise.all(
    portfolio.holdings.map(async (holding, index) => {
      const symbol = symbolMap.get(holding.symbol);
      const from = new Date();
      from.setUTCDate(from.getUTCDate() - 10);
      const history = await apiClient.getHistoricalPrices(
        holding.symbol,
        from.toISOString().slice(0, 10),
        new Date().toISOString().slice(0, 10),
        12
      );

      return {
        ...holding,
        name: symbol?.name ?? holding.symbol,
        sector: symbol?.sector ?? "Portfolio holding",
        color: palette[index % palette.length],
        trend: buildHoldingTrend(history.content, holding.lastPrice),
      };
    })
  );

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
