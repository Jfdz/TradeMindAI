import { getToken } from "next-auth/jwt";
import { NextRequest, NextResponse } from "next/server";
import { fetchTickerNewsForView } from "@/lib/enrichment-client";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ ticker: string }> },
) {
  // getToken (raw JWT) reliably yields the accessToken in App Router
  // route handlers; getServerSession() did not, leaving every proxied
  // enrichment call unauthenticated.
  const jwt = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET });
  if (!jwt) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const weeksAgo = Number(request.nextUrl.searchParams.get("weeksAgo") ?? "0");
  const token = typeof jwt.accessToken === "string" ? jwt.accessToken : undefined;
  const { ticker } = await params;

  const now = new Date();
  const to = new Date(now.getTime() - weeksAgo * 7 * 24 * 60 * 60 * 1000);
  const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000);

  const news = await fetchTickerNewsForView(
    ticker,
    from.toISOString(),
    to.toISOString(),
    20,
    token,
  );

  return NextResponse.json(news);
}
