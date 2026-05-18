import { getServerSession } from "next-auth";
import { NextRequest, NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";
import { fetchCandles } from "@/lib/dashboard/dashboard-api";

export async function GET(request: NextRequest) {
  const symbol = request.nextUrl.searchParams.get("symbol");
  if (!symbol) {
    return NextResponse.json({ error: "symbol required" }, { status: 400 });
  }

  const session = await getServerSession(authOptions);
  const candles = await fetchCandles(symbol, session?.accessToken);
  return NextResponse.json(candles);
}
