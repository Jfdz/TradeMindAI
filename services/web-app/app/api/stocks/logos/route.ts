import { auth } from "@clerk/nextjs/server";
import { NextRequest, NextResponse } from "next/server";
import { fetchProfile } from "@/lib/enrichment-client";

export async function GET(request: NextRequest) {
  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });
  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

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
