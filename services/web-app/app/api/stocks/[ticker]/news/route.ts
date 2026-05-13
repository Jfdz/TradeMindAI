import { auth } from "@clerk/nextjs/server";
import { NextRequest, NextResponse } from "next/server";
import { fetchTickerNews } from "@/lib/enrichment-client";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ ticker: string }> },
) {
  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });
  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const weeksAgo = Number(request.nextUrl.searchParams.get("weeksAgo") ?? "0");
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
