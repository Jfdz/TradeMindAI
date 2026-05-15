import { getServerSession } from "next-auth";
import { NextRequest, NextResponse } from "next/server";
import { authOptions } from "@/lib/auth";
import { fetchProfile } from "@/lib/enrichment-client";

/**
 * Stock logo lookup BFF. Fans out to trading-core's enrichment proxy,
 * which in turn pulls from market-data's Finnhub adapter behind a
 * Redis-backed cache. The web-app pod itself does not need a Finnhub
 * API key — there is a single Secret on market-data, a single cache,
 * and a single retry policy.
 *
 * Returns `{ TICKER: logoUrl | null }` for every requested ticker.
 * The component side (StockLogo) already falls back to initials when
 * the URL is missing, so this route never throws — upstream failures
 * surface as `null` per ticker.
 */
export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions);
  if (!session) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }
  const token = (session as { accessToken?: string })?.accessToken;

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
