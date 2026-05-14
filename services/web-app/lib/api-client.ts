const PROXY_BASE = "/api/proxy";

export type PagedResponse<T> = {
  content: T[];
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export type ReasoningStatus = "PENDING" | "READY" | "FALLBACK" | "FAILED";

/**
 * First news item that grounded the AI reasoning for a signal. Sourced
 * from the persisted `reasoning_facts_snapshot.news[0]` (Track C
 * audit blob). When the LLM ran with no news in window, or when the
 * artifact is missing, this field is absent on the wire.
 */
export type ReasoningNewsSnapshot = {
  headline: string | null;
  url: string | null;
  imageUrl: string | null;
  source: string | null;
  publishedAt: string | null;
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
  entryPrice?: number | null;
  reasoning?: string | null;
  reasoningStatus?: ReasoningStatus | null;
  reasoningGeneratedAt?: string | null;
  reasoningNews?: ReasoningNewsSnapshot | null;
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

export type SessionResponse = {
  id: string;
  loggedInAt: string;
  ipAddress: string | null;
  userAgent: string | null;
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
  id: string;
  symbol: string;
  quantity: number;
  averageCost: number;
  lastPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
  allocationPct: number | null;
  status: string;
  openedAt?: string;
  closedAt?: string | null;
  name?: string;
  sector?: string | null;
  trend7d?: number[];
};

export type PortfolioClosedPositionResponse = {
  id: string;
  symbol: string;
  quantity: number;
  averageCost: number;
  exitPrice: number;
  fees: number;
  realizedPnl: number;
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
  totalCapital: number | null;
  cash: number;
  realizedPnl: number;
  unrealizedPnl: number | null;
  equity: number | null;
  winRate: number | null;
  dataSource: DataSource;
  holdings: PortfolioHoldingResponse[];
  closedPositions: PortfolioClosedPositionResponse[];
};

export type DataSource = "market-data" | "partial-market-data" | "unavailable" | "none" | "missing-portfolio";

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
  adjustedClose: number | null;
};

export type MarketSymbolResponse = {
  ticker: string;
  name: string;
  exchange: string;
  sector: string;
  active: boolean;
};

export type LatestPricesResponse = {
  prices: MarketPriceResponse[];
};

export class ApiError extends Error {
  readonly status: number;
  readonly body: string;
  readonly isRateLimit: boolean;
  readonly rateLimit?: { limit: number; remaining: number; resetEpoch: number };

  constructor(status: number, body: string, statusText: string, headers?: Headers) {
    super(`Request failed with status ${status}: ${body || statusText}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
    this.isRateLimit = status === 429;
    if (this.isRateLimit && headers) {
      const limit = parseInt(headers.get("X-RateLimit-Limit") ?? "0", 10);
      const remaining = parseInt(headers.get("X-RateLimit-Remaining") ?? "0", 10);
      const reset = parseInt(headers.get("X-RateLimit-Reset") ?? "0", 10);
      if (reset > 0) {
        this.rateLimit = { limit, remaining, resetEpoch: reset };
      }
    }
  }
}

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${PROXY_BASE}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers ?? {}),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new ApiError(response.status, body, response.statusText, response.headers);
  }

  // 204 No Content / empty body endpoints (e.g., POST /positions/{id}/close)
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export const apiClient = {
  async getSignals(opts?: {
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PagedResponse<SignalResponse>> {
    const { page = 0, size = 10, sort = "generatedAt,desc" } = opts ?? {};
    return requestJson<PagedResponse<SignalResponse>>(
      `/api/v1/signals?page=${page}&size=${size}&sort=${sort}`
    );
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

  async getLatestPrices(tickers: string[]): Promise<LatestPricesResponse> {
    if (tickers.length === 0) {
      return { prices: [] };
    }

    const params = new URLSearchParams();
    for (const ticker of tickers) {
      params.append("symbols", ticker);
    }
    params.set("timeframe", "DAILY");

    try {
      return await requestJson<LatestPricesResponse>(`/api/v1/prices/latest?${params.toString()}`);
    } catch {
      return { prices: [] };
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
      return { content: [], number: 0, size, totalElements: 0, totalPages: 0 };
    }
  },

  async getHistoricalPricesBatch(
    symbols: string[],
    from: string,
    to: string,
    size = 8
  ): Promise<Record<string, MarketPriceResponse[]>> {
    if (symbols.length === 0) {
      return {};
    }
    const params = new URLSearchParams();
    for (const s of symbols) {
      params.append("symbols", s);
    }
    params.set("from", from);
    params.set("to", to);
    params.set("size", String(size));
    params.set("timeframe", "DAILY");
    try {
      return await requestJson<Record<string, MarketPriceResponse[]>>(
        `/api/v1/prices/history-batch?${params.toString()}`
      );
    } catch (err) {
      console.warn("[api-client] getHistoricalPricesBatch failed:", err);
      return {};
    }
  },

  async getSymbols(): Promise<PagedResponse<MarketSymbolResponse>> {
    try {
      return await requestJson<PagedResponse<MarketSymbolResponse>>("/api/v1/symbols");
    } catch {
      return { content: [], number: 0, size: 0, totalElements: 0, totalPages: 0 };
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

  async listMySessions(): Promise<SessionResponse[]> {
    return requestJson<SessionResponse[]>("/api/v1/users/me/sessions");
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
