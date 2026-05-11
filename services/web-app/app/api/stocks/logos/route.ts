import { NextRequest, NextResponse } from "next/server";

const FINNHUB_BASE = "https://finnhub.io/api/v1";

async function fetchLogoForTicker(ticker: string, apiKey: string): Promise<{ ticker: string; logoUrl: string | null }> {
  try {
    const res = await fetch(`${FINNHUB_BASE}/stock/profile2?symbol=${encodeURIComponent(ticker)}&token=${apiKey}`, {
      next: { revalidate: 86400 },
    });
    if (!res.ok) return { ticker, logoUrl: null };
    const data = (await res.json()) as { logo?: string };
    return { ticker, logoUrl: data.logo ?? null };
  } catch {
    return { ticker, logoUrl: null };
  }
}

export async function GET(request: NextRequest) {
  const tickers = (request.nextUrl.searchParams.get("tickers") ?? "").split(",").filter(Boolean);
  if (tickers.length === 0) {
    return NextResponse.json({});
  }

  const apiKey = process.env.FINNHUB_API_KEY ?? "";
  const results = await Promise.all(tickers.map((t) => fetchLogoForTicker(t, apiKey)));
  const logoMap = Object.fromEntries(results.map(({ ticker, logoUrl }) => [ticker, logoUrl]));
  return NextResponse.json(logoMap);
}
