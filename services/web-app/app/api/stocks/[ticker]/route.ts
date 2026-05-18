import { auth } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";
import {
  fetchEarnings,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
  fetchTickerNewsForView,
} from "@/lib/enrichment-client";

export async function GET(
  request: Request,
  { params }: { params: Promise<{ ticker: string }> },
) {
  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });
  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { ticker } = await params;
  const now = new Date();
  const from = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate()).toISOString();
  const to = now.toISOString();

  const [profileResult, newsResult, earningsResult, recsResult, peersResult] =
    await Promise.allSettled([
      fetchProfile(ticker, token),
      fetchTickerNewsForView(ticker, from, to, 20, token),
      fetchEarnings(ticker, token),
      fetchRecommendations(ticker, token),
      fetchPeers(ticker, token),
    ]);

  return NextResponse.json({
    profile: profileResult.status === "fulfilled" ? profileResult.value : null,
    news: newsResult.status === "fulfilled" ? newsResult.value : [],
    earnings: earningsResult.status === "fulfilled" ? earningsResult.value : [],
    recommendations: recsResult.status === "fulfilled" ? recsResult.value : [],
    peers: peersResult.status === "fulfilled" ? peersResult.value : [],
  });
}
