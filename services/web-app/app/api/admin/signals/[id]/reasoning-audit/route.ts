import { getServerSession } from "next-auth";
import { NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";
import { fetchReasoningAudit, isError } from "@/lib/admin/reasoning-audit-client";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const session = await getServerSession(authOptions);
  if (!session) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }
  if (!session.isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const { id } = await params;
  const token = (session as { accessToken?: string })?.accessToken;
  const result = await fetchReasoningAudit(token, id);

  if (isError(result)) {
    return NextResponse.json(
      { message: result.message },
      { status: result.status >= 500 ? 502 : result.status },
    );
  }
  return NextResponse.json(result);
}
