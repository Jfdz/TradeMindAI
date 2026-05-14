import { getServerSession } from "next-auth";
import { NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";
import { fetchAdminTickers, isError } from "@/lib/admin/reasoning-audit-client";

export async function GET() {
  const session = await getServerSession(authOptions);
  if (!session) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }
  if (!session.isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const token = (session as { accessToken?: string })?.accessToken;
  const result = await fetchAdminTickers(token);

  if (isError(result)) {
    return NextResponse.json(
      { message: result.message },
      { status: result.status >= 500 ? 502 : result.status },
    );
  }
  return NextResponse.json(result);
}
