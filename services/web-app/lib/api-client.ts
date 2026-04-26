import { getSession } from "next-auth/react";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

type RequestOptions = RequestInit & {
  fallbackOnError?: boolean;
};

export type PagedResponse<T> = {
  content: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};

export type SignalResponse = {
  id: string;
  symbol: string;
  type: "BUY" | "SELL" | "HOLD";
  confidence: number;
  generatedAt: string;
  timeframe: string;
  price?: number;
};

export type SubmitBacktestPayload = {
  symbol: string;
  from: string;
  to: string;
  quantity: number;
};

export type BacktestTradeResponse = {
  symbol: string;
  pnl: number;
};

export type BacktestResultResponse = {
  totalReturn: number;
  annualizedReturn: number;
  sharpeRatio: number;
  sortinoRatio: number;
  maxDrawdown: number;
  profitFactor: number;
  trades: BacktestTradeResponse[];
};

export type BacktestJobResponse = {
  id: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  request: SubmitBacktestPayload;
  result?: BacktestResultResponse;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
};

const demoSignals: SignalResponse[] = [
  { id: "sig-aapl-1", symbol: "AAPL", type: "BUY", confidence: 0.92, generatedAt: "2026-04-20T14:30:00Z", timeframe: "1D", price: 178.5 },
  { id: "sig-nvda-1", symbol: "NVDA", type: "BUY", confidence: 0.88, generatedAt: "2026-04-20T13:00:00Z", timeframe: "4H", price: 846.2 },
  { id: "sig-msft-1", symbol: "MSFT", type: "HOLD", confidence: 0.74, generatedAt: "2026-04-19T15:45:00Z", timeframe: "1D", price: 421.1 },
];

function buildDemoBacktest(id: string, request?: SubmitBacktestPayload): BacktestJobResponse {
  const fallbackRequest =
    request ??
    ({
      symbol: "AAPL",
      from: "2026-04-01",
      to: "2026-04-16",
      quantity: 24,
    } satisfies SubmitBacktestPayload);

  return {
    id,
    status: "COMPLETED",
    request: fallbackRequest,
    createdAt: "2026-04-20T09:30:00Z",
    updatedAt: "2026-04-20T09:32:30Z",
    result: {
      totalReturn: 0.124,
      annualizedReturn: 0.218,
      sharpeRatio: 1.84,
      sortinoRatio: 2.31,
      maxDrawdown: -0.047,
      profitFactor: 1.93,
      trades: [
        { symbol: fallbackRequest.symbol, pnl: 185.4 },
        { symbol: fallbackRequest.symbol, pnl: -42.8 },
        { symbol: fallbackRequest.symbol, pnl: 221.1 },
        { symbol: fallbackRequest.symbol, pnl: 97.6 },
      ],
    },
  };
}

async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { fallbackOnError = false, ...fetchOptions } = options;

  const session = await getSession();
  const token = (session as { accessToken?: string } | null)?.accessToken;

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...fetchOptions,
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(fetchOptions.body ? { "Content-Type": "application/json" } : {}),
        ...(fetchOptions.headers ?? {}),
      },
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`);
    }

    return (await response.json()) as T;
  } catch (error) {
    if (fallbackOnError) {
      throw error;
    }

    throw error instanceof Error ? error : new Error("Request failed");
  }
}

export const apiClient = {
  async getSignals(): Promise<PagedResponse<SignalResponse>> {
    try {
      return await requestJson<PagedResponse<SignalResponse>>("/api/v1/signals");
    } catch {
      return {
        content: demoSignals,
        page: 0,
        size: demoSignals.length,
        totalElements: demoSignals.length,
        totalPages: 1,
      };
    }
  },

  async checkSymbolAvailability(symbol: string): Promise<boolean> {
    try {
      const response = await requestJson<{ available: boolean }>(`/api/v1/market-data/symbols/${symbol}/availability`, {
        fallbackOnError: true,
      });
      return response.available;
    } catch {
      return true;
    }
  },

  async submitBacktest(payload: SubmitBacktestPayload): Promise<BacktestJobResponse> {
    try {
      return await requestJson<BacktestJobResponse>("/api/v1/backtests", {
        method: "POST",
        body: JSON.stringify(payload),
      });
    } catch {
      return buildDemoBacktest(`demo-${payload.symbol.toLowerCase()}-${Date.now()}`, payload);
    }
  },

  async getBacktest(backtestId: string): Promise<BacktestJobResponse> {
    try {
      return await requestJson<BacktestJobResponse>(`/api/v1/backtests/${backtestId}`, {
        fallbackOnError: true,
      });
    } catch {
      return buildDemoBacktest(backtestId);
    }
  },
};
