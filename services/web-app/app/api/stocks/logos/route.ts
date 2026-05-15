import { getToken } from "next-auth/jwt";
import { NextRequest, NextResponse } from "next/server";
import { fetchProfile } from "@/lib/enrichment-client";

/**
 * Stock logo lookup BFF. Fans out to trading-core's enrichment proxy,
 * which in turn pulls from market-data's Finnhub adapter behind a
 * Redis-backed cache. The web-app pod itself does not need a Finnhub
 * API key — there is a single Secret on market-data, a single cache,
 * and a single retry policy.
 *
 * Primary callers now use the client-side apiClient path; this route
 * is retained as a server-side fallback and is fixed to use getToken
 * (the App Router-reliable JWT accessor) — getServerSession did not
 * surface the custom accessToken, which 401'd every upstream call.
 *
 * Returns `{ TICKER: logoUrl | null }` for every requested ticker.
 * StockLogo falls back to initials when the URL is missing.
 */
export async function GET(request: NextRequest) {
  const jwt = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
  if (!jwt) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }
  const token = typeof jwt.accessToken === "string" ? jwt.accessToken : undefined;

  const tickers = (request.nextUrl.searchParams.get("tickers") ?? "")
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);
  if (tickers.length === 0) {
    return NextResponse.json({});
  }

  const results = await Promise.all(
    tickers.map(async (ticker) => {
      try {
        const profile = await fetchProfile(ticker, token);
        return [ticker, profile?.logo ?? null] as const;
      } catch {
        return [ticker, null] as const;
      }
    }),
  );

  return NextResponse.json(Object.fromEntries(results));
}
