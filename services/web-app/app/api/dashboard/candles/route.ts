import { auth } from "@clerk/nextjs/server";
import { NextRequest, NextResponse } from "next/server";

import { fetchCandles } from "@/lib/dashboard/dashboard-api";

export async function GET(request: NextRequest) {
  const symbol = request.nextUrl.searchParams.get("symbol");
  if (!symbol) {
    return NextResponse.json({ error: "symbol required" }, { status: 400 });
  }

  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });
  const candles = await fetchCandles(symbol, token ?? undefined);
  return NextResponse.json(candles);
}
