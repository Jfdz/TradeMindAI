import { auth, currentUser } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";

import { authOptions } from "@/lib/auth";

// Read server env per-request, never at module load. Module-level reads are
// captured when the route module is first evaluated during the build/trace
// phase, before the container's runtime env (INTERNAL_SECRET from the k8s
// secret) is injected — yielding a permanent empty value in production.
export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(request: Request) {
  const { userId } = await auth();
  if (!userId) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const user = await currentUser();
  const isAdmin = (user?.publicMetadata as { role?: string } | null)?.role === "admin";
  if (!isAdmin) {
    return NextResponse.json({ message: "Forbidden" }, { status: 403 });
  }

  const AI_ENGINE_URL =
    process.env.AI_ENGINE_SERVICE_URL ?? process.env.AI_ENGINE_URL ?? "http://localhost:8000";
  const INTERNAL_SECRET = process.env.INTERNAL_SECRET ?? "";

  if (!INTERNAL_SECRET) {
    return NextResponse.json({ message: "Server misconfiguration: INTERNAL_SECRET not set" }, { status: 503 });
  }

  let body: { tickers?: string[] };
  try {
    body = (await request.json()) as { tickers?: string[] };
  } catch {
    body = {};
  }

  const tickers = Array.isArray(body.tickers) && body.tickers.length > 0 ? body.tickers : null;
  if (!tickers) {
    return NextResponse.json({ message: "tickers array is required" }, { status: 400 });
  }

  const upstream = await fetch(`${AI_ENGINE_URL}/api/v1/predict/publish`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "X-Internal-Secret": INTERNAL_SECRET,
    },
    body: JSON.stringify({ tickers }),
  });

  const data = await upstream.json().catch(() => ({}));

  if (!upstream.ok) {
    return NextResponse.json(
      { message: (data as { detail?: string }).detail ?? `AI engine error ${upstream.status}` },
      { status: upstream.status >= 500 ? 502 : upstream.status }
    );
  }

  return NextResponse.json(data, { status: 200 });
}
