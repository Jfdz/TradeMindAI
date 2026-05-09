const API_BASE_URL =
  process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

// --- Types ---

export type CompanyProfileResponse = {
  ticker: string;
  name: string;
  logo: string | null;
  country: string | null;
  currency: string | null;
  exchange: string | null;
  ipo: string | null;
  marketCap: number | null;
  phone: string | null;
  weburl: string | null;
  industry: string | null;
};

export type NewsItemResponse = {
  id: number;
  headline: string;
  publishedAt: string;
  category: string | null;
  source: string | null;
  summary: string | null;
  url: string | null;
  image: string | null;
};

export type EarningsEventResponse = {
  ticker: string;
  period: string;
  year: number;
  quarter: number;
  epsActual: number | null;
  epsEstimate: number | null;
  revenueActual: number | null;
  revenueEstimate: number | null;
};

export type AnalystRecommendationResponse = {
  ticker: string;
  period: string;
  buy: number;
  hold: number;
  sell: number;
  strongBuy: number;
  strongSell: number;
};

// --- HTTP helper ---

async function enrichmentFetch<T>(path: string, token?: string): Promise<T | null> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    cache: "no-store",
  });

  if (!response.ok) {
    return null;
  }

  return (await response.json()) as T;
}

// --- Fetch functions ---

export async function fetchProfile(
  ticker: string,
  token?: string,
): Promise<CompanyProfileResponse | null> {
  return enrichmentFetch<CompanyProfileResponse>(
    `/api/v1/enrichment/profile/${encodeURIComponent(ticker)}`,
    token,
  );
}

export async function fetchMarketNews(
  category = "general",
  limit = 20,
  token?: string,
): Promise<NewsItemResponse[]> {
  const result = await enrichmentFetch<NewsItemResponse[]>(
    `/api/v1/enrichment/news?category=${encodeURIComponent(category)}&limit=${limit}`,
    token,
  );
  return result ?? [];
}

export async function fetchTickerNews(
  ticker: string,
  from: string,
  to: string,
  limit = 20,
  token?: string,
): Promise<NewsItemResponse[]> {
  const result = await enrichmentFetch<NewsItemResponse[]>(
    `/api/v1/enrichment/news/${encodeURIComponent(ticker)}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&limit=${limit}`,
    token,
  );
  return result ?? [];
}

export async function fetchEarnings(
  ticker: string,
  token?: string,
): Promise<EarningsEventResponse[]> {
  const result = await enrichmentFetch<EarningsEventResponse[]>(
    `/api/v1/enrichment/earnings/${encodeURIComponent(ticker)}`,
    token,
  );
  return result ?? [];
}

export async function fetchRecommendations(
  ticker: string,
  token?: string,
): Promise<AnalystRecommendationResponse[]> {
  const result = await enrichmentFetch<AnalystRecommendationResponse[]>(
    `/api/v1/enrichment/recommendations/${encodeURIComponent(ticker)}`,
    token,
  );
  return result ?? [];
}

export async function fetchPeers(ticker: string, token?: string): Promise<string[]> {
  const result = await enrichmentFetch<string[]>(
    `/api/v1/enrichment/peers/${encodeURIComponent(ticker)}`,
    token,
  );
  return result ?? [];
}
