import { getToken } from "next-auth/jwt";
import { NextRequest, NextResponse } from "next/server";
import {
  fetchEarnings,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
  fetchTickerNewsForView,
} from "@/lib/enrichment-client";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ ticker: string }> },
) {
  // getToken reads the raw next-auth JWT from the request cookie and
  // returns the fields set in the jwt() callback (incl. accessToken).
  // getServerSession() did not surface the custom accessToken field in
  // App Router route handlers — that was why every enrichment proxy
  // call went out unauthenticated and the backend 401'd to null.
  const jwt = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
  if (!jwt) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const token = typeof jwt.accessToken === "string" ? jwt.accessToken : undefined;
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
