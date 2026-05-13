import { auth } from "@clerk/nextjs/server";
import {
  fetchEarnings,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
  fetchTickerNews,
  type AnalystRecommendationResponse,
  type CompanyProfileResponse,
  type EarningsEventResponse,
  type NewsItemResponse,
} from "@/lib/enrichment-client";

export type StockDetailData = {
  profile: CompanyProfileResponse | null;
  news: NewsItemResponse[];
  earnings: EarningsEventResponse[];
  recommendations: AnalystRecommendationResponse[];
  peers: string[];
};

async function getToken(): Promise<string | undefined> {
  const { getToken } = await auth();
  return (await getToken({ template: "backend" })) ?? undefined;
}

export async function fetchStockDetail(ticker: string): Promise<StockDetailData> {
  const token = await getToken();
  const now = new Date();
  const from = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate()).toISOString();
  const to = now.toISOString();

  const [profileResult, newsResult, earningsResult, recsResult, peersResult] =
    await Promise.allSettled([
      fetchProfile(ticker, token),
      fetchTickerNews(ticker, from, to, 20, token),
      fetchEarnings(ticker, token),
      fetchRecommendations(ticker, token),
      fetchPeers(ticker, token),
    ]);

  return {
    profile: profileResult.status === "fulfilled" ? profileResult.value : null,
    news: newsResult.status === "fulfilled" ? newsResult.value : [],
    earnings: earningsResult.status === "fulfilled" ? earningsResult.value : [],
    recommendations: recsResult.status === "fulfilled" ? recsResult.value : [],
    peers: peersResult.status === "fulfilled" ? peersResult.value : [],
  };
}
