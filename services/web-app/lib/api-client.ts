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
  stopLossPct?: number | null;
  takeProfitPct?: number | null;
  predictedChangePct?: number | null;
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
  winRate: number;
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

export type UserProfileResponse = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  timezone: string;
  plan: string;
  createdAt?: string;
  active: boolean;
};

export type NotificationPreferencesResponse = {
  userId: string;
  signalDigest: boolean;
  liveAlerts: boolean;
  riskWarnings: boolean;
  strategyChanges: boolean;
  weeklyRecap: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type UpdateUserProfilePayload = {
  firstName: string;
  lastName: string;
  timezone: string;
};

export type UpdateNotificationPreferencesPayload = {
  signalDigest: boolean;
  liveAlerts: boolean;
  riskWarnings: boolean;
  strategyChanges: boolean;
  weeklyRecap: boolean;
};

export type PortfolioHoldingResponse = {
  symbol: string;
  quantity: number;
  averageCost: number;
  lastPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  allocationPct: number;
  status: string;
  openedAt?: string;
  closedAt?: string | null;
};

export type AddPositionPayload = {
  ticker: string;
  quantity: number;
  entryPrice: number;
  purchaseDate?: string;
  fees?: number;
  notes?: string;
};

export type ClosePositionPayload = {
  exitPrice: number;
  closedAt?: string;
  fees?: number;
};

export type PortfolioOverviewResponse = {
  userId: string;
  initialCapital: number;
  cash: number;
  realizedPnl: number;
  unrealizedPnl: number;
  equity: number;
  winRate: number;
  holdings: PortfolioHoldingResponse[];
};

export type MarketPriceResponse = {
  ticker: string;
  date: string;
  timeFrame: string;
  ohlcv: {
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
  };
  adjustedClose: number;
};

export type MarketSymbolResponse = {
  ticker: string;
  name: string;
  exchange: string;
  sector: string;
  active: boolean;
};

const demoPrices: Record<string, number> = {
  "AAPL": 178.5,
  "NVDA": 846.2,
  "MSFT": 421.1,
  "TSLA": 187.8,
  "AMD": 171.25,
  "BTC/USDT": 68412.5,
  "ETH/USDT": 3328.4,
};

const demoSignals: SignalResponse[] = [
  { id: "sig-btc-1", symbol: "BTC/USDT", type: "BUY", confidence: 0.942, generatedAt: "2026-04-20T14:30:00Z", timeframe: "4H", stopLossPct: 0.53, takeProfitPct: 2.70, predictedChangePct: 2.7 },
  { id: "sig-eth-1", symbol: "ETH/USDT", type: "BUY", confidence: 0.888, generatedAt: "2026-04-20T13:00:00Z", timeframe: "1H", stopLossPct: 1.63, takeProfitPct: 2.93, predictedChangePct: 2.9 },
  { id: "sig-aapl-1", symbol: "AAPL", type: "HOLD", confidence: 0.742, generatedAt: "2026-04-19T15:45:00Z", timeframe: "1D", stopLossPct: 1.80, takeProfitPct: 2.59, predictedChangePct: 0.2 },
  { id: "sig-tsla-1", symbol: "TSLA", type: "SELL", confidence: 0.811, generatedAt: "2026-04-18T16:15:00Z", timeframe: "4H", stopLossPct: 2.92, takeProfitPct: 5.37, predictedChangePct: -4.1 },
  { id: "sig-amd-1", symbol: "AMD", type: "BUY", confidence: 0.792, generatedAt: "2026-04-17T12:10:00Z", timeframe: "1D", stopLossPct: 2.18, takeProfitPct: 4.65, predictedChangePct: 3.8 },
  { id: "sig-msft-1", symbol: "MSFT", type: "HOLD", confidence: 0.691, generatedAt: "2026-04-16T11:05:00Z", timeframe: "1D", stopLossPct: 1.60, takeProfitPct: 3.25, predictedChangePct: 0.5 },
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
      winRate: 0.75,
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
      const response = await requestJson<{ available: boolean }>(`/api/v1/backtests/symbols/${symbol}/available`, {
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
        fallbackOnError: true,
      });
    } catch {
      return buildDemoBacktest(`demo-${Date.now()}`, payload);
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

  async getCurrentUser(): Promise<UserProfileResponse> {
    try {
      return await requestJson<UserProfileResponse>("/api/v1/users/me");
    } catch {
      const session = await getSession();
      return {
        id: (session?.user as { id?: string } | undefined)?.id ?? "local-demo-user",
        email: session?.user?.email ?? "user@tradermind.ai",
        firstName: session?.user?.name?.split(" ")[0] ?? "TradeMind",
        lastName: session?.user?.name?.split(" ").slice(1).join(" ") || "Operator",
        timezone: "Europe/Madrid",
        plan: "FREE",
        createdAt: undefined,
        active: true,
      };
    }
  },

  async getSignal(signalId: string): Promise<SignalResponse> {
    try {
      return await requestJson<SignalResponse>(`/api/v1/signals/${signalId}`);
    } catch {
      const fallback = demoSignals.find((signal) => signal.id === signalId) ?? demoSignals[0];
      return fallback;
    }
  },

  async getLatestPrice(ticker: string): Promise<MarketPriceResponse | null> {
    try {
      return await requestJson<MarketPriceResponse>(`/api/v1/prices/${ticker}/latest?timeframe=DAILY`, {
        fallbackOnError: true,
      });
    } catch {
      const fallbackClose = demoPrices[ticker];
      if (fallbackClose == null) return null;
      return {
        ticker,
        date: new Date().toISOString().slice(0, 10),
        timeFrame: "DAILY",
        ohlcv: { open: fallbackClose, high: fallbackClose, low: fallbackClose, close: fallbackClose, volume: 0 },
        adjustedClose: fallbackClose,
      };
    }
  },

  async getHistoricalPrices(
    ticker: string,
    from: string,
    to: string,
    size = 30
  ): Promise<PagedResponse<MarketPriceResponse>> {
    try {
      return await requestJson<PagedResponse<MarketPriceResponse>>(
        `/api/v1/prices/${ticker}/history?timeframe=DAILY&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&size=${size}`
      );
    } catch {
      return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
    }
  },

  async getSymbols(): Promise<PagedResponse<MarketSymbolResponse>> {
    try {
      return await requestJson<PagedResponse<MarketSymbolResponse>>("/api/v1/symbols");
    } catch {
      return { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 };
    }
  },

  async updateCurrentUser(payload: UpdateUserProfilePayload): Promise<UserProfileResponse> {
    return await requestJson<UserProfileResponse>("/api/v1/users/me", {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  async getNotificationPreferences(): Promise<NotificationPreferencesResponse> {
    try {
      return await requestJson<NotificationPreferencesResponse>("/api/v1/users/me/notifications");
    } catch {
      const session = await getSession();
      return {
        userId: (session?.user as { id?: string } | undefined)?.id ?? "local-demo-user",
        signalDigest: true,
        liveAlerts: true,
        riskWarnings: true,
        strategyChanges: false,
        weeklyRecap: true,
        createdAt: undefined,
        updatedAt: undefined,
      };
    }
  },

  async updateNotificationPreferences(payload: UpdateNotificationPreferencesPayload): Promise<NotificationPreferencesResponse> {
    return await requestJson<NotificationPreferencesResponse>("/api/v1/users/me/notifications", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  async addPosition(payload: { ticker: string; quantity: number; entryPrice: number }): Promise<{ id: string; ticker: string; quantity: number; entryPrice: number; status: string; openedAt: string }> {
    return await requestJson("/api/v1/portfolio/positions", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async deletePosition(positionId: string): Promise<void> {
    await requestJson(`/api/v1/portfolio/positions/${positionId}`, { method: "DELETE" });
  },

  async getPortfolio(): Promise<PortfolioOverviewResponse> {
    try {
      return await requestJson<PortfolioOverviewResponse>("/api/v1/portfolio");
    } catch {
      return {
        userId: "local-demo-user",
        initialCapital: 0,
        cash: 0,
        realizedPnl: 0,
        unrealizedPnl: 0,
        equity: 0,
        winRate: 0,
        holdings: [],
      };
    }
  },

  async addPosition(payload: AddPositionPayload): Promise<{ id: string }> {
    return requestJson<{ id: string }>("/api/v1/portfolio/positions", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async closePosition(positionId: string, payload: ClosePositionPayload): Promise<void> {
    await requestJson<void>(`/api/v1/portfolio/positions/${positionId}/close`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async deletePosition(positionId: string): Promise<void> {
    await requestJson<void>(`/api/v1/portfolio/positions/${positionId}`, {
      method: "DELETE",
    });
  },
};
