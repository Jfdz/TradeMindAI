import { auth, currentUser } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";

import { fetchReasoningAudit, isError } from "@/lib/admin/reasoning-audit-client";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { userId, getToken } = await auth();
  if (!userId) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const user = await currentUser();
  const isAdmin = (user?.publicMetadata as { role?: string } | null)?.role === "admin";
  if (!isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const { id } = await params;
  const token = await getToken({ template: "backend" });
  const result = await fetchReasoningAudit(token ?? undefined, id);

  if (isError(result)) {
    return NextResponse.json(
      { message: result.message },
      { status: result.status >= 500 ? 502 : result.status },
    );
  }
  return NextResponse.json(result);
}
