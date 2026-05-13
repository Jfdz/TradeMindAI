import type { SeriesMarker, Time } from "lightweight-charts";
import { auth } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";

import type {
  LatestPricesResponse,
  MarketPriceResponse,
  MarketSymbolResponse,
  PagedResponse,
  PortfolioOverviewResponse,
  SignalResponse,
} from "@/lib/api-client";
import type { DashboardCandle, DashboardPageData, EnrichedHolding } from "@/lib/dashboard/dashboard-api";
import { buildHoldingTrend } from "@/lib/dashboard/dashboard-api";
import { convertPricesToCandles, deriveSignal } from "@/lib/dashboard/signal-derivation";
import { assignSymbolColors } from "@/lib/dashboard/symbol-colors";
import { signalTypeColor } from "@/lib/signal-utils";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8082";

function buildChartMarker(
  lastCandle: DashboardCandle,
  targetSignal: ReturnType<typeof deriveSignal> | null,
): SeriesMarker<Time> | null {
  if (!lastCandle || !targetSignal) {
    return null;
  }
  const signalType = targetSignal.type;
  const getShape = (type: string): "arrowDown" | "arrowUp" | "circle" => {
    if (type === "SELL") return "arrowDown";
    if (type === "BUY") return "arrowUp";
    return "circle";
  };
  return {
    time: lastCandle.time as Time,
    position: signalType === "SELL" ? "aboveBar" : "belowBar",
    color: signalTypeColor(signalType),
    shape: getShape(signalType),
    text: targetSignal.symbol,
  };
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
  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });

  if (!token) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }

  try {
    const [portfolioResult, signalResult, symbolResponse] = await Promise.all([
      backendJsonSafe<PortfolioOverviewResponse>("/api/v1/portfolio", token),
      backendJsonSafe<PagedResponse<SignalResponse>>("/api/v1/signals?size=200&sort=generatedAt,desc", token),
      backendJson<PagedResponse<MarketSymbolResponse>>("/api/v1/symbols", token, true),
    ]);

    if (!portfolioResult.ok || !signalResult.ok) {
      const failed = (portfolioResult.ok ? signalResult : portfolioResult) as { ok: false; status: number };
      return failed.status === 401
        ? NextResponse.json({ message: "Authentication required" }, { status: 401 })
        : NextResponse.json({ message: `Upstream error ${failed.status}` }, { status: 502 });
    }

    const portfolio = portfolioResult.data;
    const signalResponse = signalResult.data;

    const symbolMap = new Map((symbolResponse.content ?? []).map((symbol) => [symbol.ticker, symbol]));
    const signalSymbols = (signalResponse.content ?? []).map((signal) => signal.symbol);
    const holdingSymbols = (portfolio.holdings ?? []).map((holding) => holding.symbol);
    const uniqueSymbols = Array.from(new Set([...signalSymbols, ...holdingSymbols]));

    const latestPriceParams = new URLSearchParams();
    for (const symbol of uniqueSymbols) {
      latestPriceParams.append("symbols", symbol);
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

    const colorMap = assignSymbolColors((portfolio.holdings ?? []).map((h) => h.symbol));

    let holdingHistory: Record<string, MarketPriceResponse[]> = {};
    if (holdingSymbols.length > 0) {
      const holdingFrom = new Date();
      holdingFrom.setUTCDate(holdingFrom.getUTCDate() - 7);
      const batchParams = new URLSearchParams();
      for (const s of holdingSymbols) batchParams.append("symbols", s);
      batchParams.set("from", holdingFrom.toISOString().slice(0, 10));
      batchParams.set("to", new Date().toISOString().slice(0, 10));
      batchParams.set("size", "8");
      batchParams.set("timeframe", "DAILY");
      holdingHistory = await backendJson<Record<string, MarketPriceResponse[]>>(
        `/api/v1/prices/history-batch?${batchParams.toString()}`,
        token,
        true
      );
    }

    const holdings: EnrichedHolding[] = (portfolio.holdings ?? []).map((holding) => ({
      ...holding,
      name: symbolMap.get(holding.symbol)?.name ?? holding.symbol,
      sector: symbolMap.get(holding.symbol)?.sector ?? "Portfolio holding",
      color: colorMap.get(holding.symbol)!,
      trend: buildHoldingTrend(holdingHistory[holding.symbol] ?? []),
    }));

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

      chartCandles = (history.content ?? []).length > 0
        ? convertPricesToCandles(history.content ?? [])
        : [];

      const lastCandle = chartCandles[chartCandles.length - 1];
      chartMarker = buildChartMarker(lastCandle, targetSignal);
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
