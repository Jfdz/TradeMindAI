import { getServerSession } from "next-auth";
import { NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";
import type {
  LatestPricesResponse,
  MarketPriceResponse,
  MarketSymbolResponse,
  PagedResponse,
  PortfolioOverviewResponse,
  SignalResponse,
} from "@/lib/api-client";
import type { DashboardCandle, DashboardPageData, EnrichedHolding, FilteredSignal } from "@/lib/dashboard/dashboard-api";
import { buildHoldingTrend } from "@/lib/dashboard/dashboard-api";
import { buildSignalReasoning, signalTypeColor } from "@/lib/signal-utils";

const API_BASE_URL = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";
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

function deriveSignal(signal: SignalResponse, latestPrice: number | null): FilteredSignal {
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

function convertPricesToCandles(prices: MarketPriceResponse[]): DashboardCandle[] {
  return prices
    .slice()
    .sort((left, right) => new Date(left.date).getTime() - new Date(right.date).getTime())
    .map((price) => ({
      time: toBusinessDay(price.date),
      open: price.ohlcv.open,
      high: price.ohlcv.high,
      low: price.ohlcv.low,
      close: price.adjustedClose ?? price.ohlcv.close,
      volume: price.ohlcv.volume,
    }));
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

async function backendJson<T>(path: string, token?: string, optional = false): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    if (optional) {
      return {} as T;
    }
    throw new Error(`Backend request failed with status ${response.status}`);
  }

  return (await response.json()) as T;
}

async function backendJsonSafe<T>(
  path: string,
  token?: string
): Promise<{ ok: true; data: T } | { ok: false; status: number }> {
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      cache: "no-store",
    });
    if (!response.ok) {
      return { ok: false, status: response.status };
    }
    return { ok: true, data: (await response.json()) as T };
  } catch {
    return { ok: false, status: 503 };
  }
}

export async function GET() {
  const session = await getServerSession(authOptions);
  const token = session?.accessToken;

  if (!token) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }

  try {
    const [portfolioResult, signalResult, symbolResponse] = await Promise.all([
      backendJsonSafe<PortfolioOverviewResponse>("/api/v1/portfolio", token),
      backendJsonSafe<PagedResponse<SignalResponse>>("/api/v1/signals", token),
      backendJson<PagedResponse<MarketSymbolResponse>>("/api/v1/symbols", token, true),
    ]);

    if (!portfolioResult.ok || !signalResult.ok) {
      const failed = (portfolioResult.ok === false ? portfolioResult : signalResult) as {
        ok: false;
        status: number;
      };
      if (failed.status === 401) {
        return NextResponse.json({ message: "Authentication required" }, { status: 401 });
      }
      return NextResponse.json({ message: `Upstream error ${failed.status}` }, { status: 502 });
    }

    const portfolio = portfolioResult.data;
    const signalResponse = signalResult.data;

    const symbolMap = new Map((symbolResponse.content ?? []).map((symbol) => [symbol.ticker, symbol]));
    const signalSymbols = (signalResponse.content ?? []).map((signal) => signal.symbol);
    const holdingSymbols = (portfolio.holdings ?? []).map((holding) => holding.symbol);
    const uniqueSymbols = Array.from(new Set([...signalSymbols, ...holdingSymbols]));

    const latestPriceParams = new URLSearchParams();
    for (const symbol of uniqueSymbols) {
      latestPriceParams.append("tickers", symbol);
    }
    latestPriceParams.set("timeframe", "DAILY");

    const latestPrices =
      uniqueSymbols.length > 0
        ? await backendJson<LatestPricesResponse>(`/api/v1/prices/latest?${latestPriceParams.toString()}`, token, true)
        : { prices: [] };
    const latestPriceBySymbol = new Map(
      (latestPrices.prices ?? []).map((price) => [price.ticker, price.adjustedClose ?? price.ohlcv.close] as const)
    );

    const signals = (signalResponse.content ?? [])
      .map((signal) => deriveSignal(signal, latestPriceBySymbol.get(signal.symbol) ?? null))
      .sort((left, right) => new Date(right.generatedAt).getTime() - new Date(left.generatedAt).getTime());

    const holdings: EnrichedHolding[] = await Promise.all(
      (portfolio.holdings ?? []).map(async (holding, index) => {
        const from = new Date();
        from.setUTCDate(from.getUTCDate() - 10);
        const history = await backendJson<PagedResponse<MarketPriceResponse>>(
          `/api/v1/prices/${holding.symbol}/history?timeframe=DAILY&from=${from.toISOString().slice(0, 10)}&to=${new Date().toISOString().slice(0, 10)}&size=12`,
          token,
          true
        );

        return {
          ...holding,
          name: symbolMap.get(holding.symbol)?.name ?? holding.symbol,
          sector: symbolMap.get(holding.symbol)?.sector ?? "Portfolio holding",
          color: palette[index % palette.length],
          trend: buildHoldingTrend(history.content ?? [], holding.lastPrice),
        };
      })
    );

    const targetSignal = signals[0] ?? null;
    const targetHolding = holdings[0] ?? null;
    const targetSymbol = targetSignal?.symbol ?? targetHolding?.symbol ?? null;

    let chartCandles: DashboardCandle[] = [];
    let chartMarker: DashboardPageData["chartMarker"] = null;

    if (targetSymbol) {
      const from = new Date();
      from.setUTCDate(from.getUTCDate() - 8);
      const history = await backendJson<PagedResponse<MarketPriceResponse>>(
        `/api/v1/prices/${targetSymbol}/history?timeframe=DAILY&from=${from.toISOString().slice(0, 10)}&to=${new Date().toISOString().slice(0, 10)}&size=24`,
        token,
        true
      );

      const latestPrice = latestPriceBySymbol.get(targetSymbol) ?? null;
      chartCandles = (history.content ?? []).length
        ? convertPricesToCandles(history.content ?? [])
        : synthesizeCandles(latestPrice ?? 100, targetSignal?.generatedAt ?? new Date().toISOString());

      const lastCandle = chartCandles[chartCandles.length - 1];
      if (lastCandle && targetSignal) {
        const signalType = targetSignal.type;
        chartMarker = {
          time: lastCandle.time,
          position: signalType === "SELL" ? "aboveBar" : "belowBar",
          color: signalTypeColor(signalType),
          shape: signalType === "SELL" ? "arrowDown" : signalType === "BUY" ? "arrowUp" : "circle",
          text: targetSignal.symbol,
        };
      }
    }

    return NextResponse.json<DashboardPageData>({
      portfolio,
      signals,
      holdings,
      chartCandles,
      chartMarker,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unable to load dashboard";
    return NextResponse.json({ message }, { status: 500 });
  }
}
