import { getSession } from "next-auth/react";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

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

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = await getSession();
  const token = (session as { accessToken?: string } | null)?.accessToken;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers ?? {}),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return (await response.json()) as T;
}

export const apiClient = {
  async getSignals(): Promise<PagedResponse<SignalResponse>> {
    return requestJson<PagedResponse<SignalResponse>>("/api/v1/signals");
  },

  async checkSymbolAvailability(symbol: string): Promise<boolean> {
    const response = await requestJson<{ available: boolean }>(`/api/v1/backtests/symbols/${symbol}/available`);
    return response.available;
  },

  async submitBacktest(payload: SubmitBacktestPayload): Promise<BacktestJobResponse> {
    return requestJson<BacktestJobResponse>("/api/v1/backtests", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  async getBacktest(backtestId: string): Promise<BacktestJobResponse> {
    return requestJson<BacktestJobResponse>(`/api/v1/backtests/${backtestId}`);
  },

  async getCurrentUser(): Promise<UserProfileResponse> {
    return requestJson<UserProfileResponse>("/api/v1/users/me");
  },

  async getSignal(signalId: string): Promise<SignalResponse> {
    return requestJson<SignalResponse>(`/api/v1/signals/${signalId}`);
  },

  async getLatestPrice(ticker: string): Promise<MarketPriceResponse | null> {
    try {
      return await requestJson<MarketPriceResponse>(`/api/v1/prices/${ticker}/latest?timeframe=DAILY`);
    } catch {
      return null;
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
    return requestJson<UserProfileResponse>("/api/v1/users/me", {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
  },

  async getNotificationPreferences(): Promise<NotificationPreferencesResponse> {
    return requestJson<NotificationPreferencesResponse>("/api/v1/users/me/notifications");
  },

  async updateNotificationPreferences(payload: UpdateNotificationPreferencesPayload): Promise<NotificationPreferencesResponse> {
    return requestJson<NotificationPreferencesResponse>("/api/v1/users/me/notifications", {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  async getPortfolio(): Promise<PortfolioOverviewResponse> {
    return requestJson<PortfolioOverviewResponse>("/api/v1/portfolio");
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
};
