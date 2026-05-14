import { getServerSession } from "next-auth";
import { NextRequest, NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";
import { fetchAdminSignals, isError } from "@/lib/admin/reasoning-audit-client";

export async function GET(request: NextRequest) {
  const session = await getServerSession(authOptions);
  if (!session) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }
  if (!session.isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const params = request.nextUrl.searchParams;
  const ticker = params.get("ticker") ?? undefined;
  const page = Number(params.get("page") ?? "0");
  const size = Number(params.get("size") ?? "25");

  const token = (session as { accessToken?: string })?.accessToken;
  const result = await fetchAdminSignals(token, { ticker, page, size });

  if (isError(result)) {
    return NextResponse.json(
      { message: result.message },
      { status: result.status >= 500 ? 502 : result.status },
    );
  }
  return NextResponse.json(result);
}
