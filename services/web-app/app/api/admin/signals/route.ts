import { auth, currentUser } from "@clerk/nextjs/server";
import { NextRequest, NextResponse } from "next/server";

import { fetchAdminSignals, isError } from "@/lib/admin/reasoning-audit-client";

export async function GET(request: NextRequest) {
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const user = await currentUser();
  const isAdmin = (user?.publicMetadata as { role?: string } | null)?.role === "admin";
  if (!isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const params = request.nextUrl.searchParams;
  const ticker = params.get("ticker") ?? undefined;
  const page = Number(params.get("page") ?? "0");
  const size = Number(params.get("size") ?? "25");

  const token = await getToken({ template: "backend" });
  const result = await fetchAdminSignals(token ?? undefined, { ticker, page, size });

  if (isError(result)) {
    return NextResponse.json(
      { message: result.message },
      { status: result.status >= 500 ? 502 : result.status },
    );
  }
  return NextResponse.json(result);
}
