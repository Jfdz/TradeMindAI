import { auth } from "@clerk/nextjs/server";
import { NextRequest, NextResponse } from "next/server";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8082";

const ALLOWED_METHODS = new Set(["GET", "POST", "PUT", "PATCH", "DELETE"]);

async function proxyRequest(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const method = request.method.toUpperCase();
  if (!ALLOWED_METHODS.has(method)) {
    return NextResponse.json({ error: "Method not allowed" }, { status: 405 });
  }

  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });

  if (!token) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { path } = await params;
  const targetPath = "/" + path.join("/");
  const search = request.nextUrl.search;
  const upstream = `${API_BASE_URL}${targetPath}${search}`;

  const requestInit: RequestInit = {
    method,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(request.body ? { "Content-Type": request.headers.get("content-type") ?? "application/json" } : {}),
    },
    cache: "no-store",
  };

  if (request.body) {
    requestInit.body = await request.text();
  }

  const response = await fetch(upstream, requestInit);

  const body = await response.text();
  const headers = new Headers();
  headers.set("Content-Type", response.headers.get("Content-Type") ?? "application/json");
  for (const [key, value] of response.headers.entries()) {
    if (key.toLowerCase().startsWith("x-")) headers.set(key, value);
  }

  return new NextResponse(body || null, {
    status: response.status,
    headers,
  });
}

export const GET = proxyRequest;
export const POST = proxyRequest;
export const PUT = proxyRequest;
export const PATCH = proxyRequest;
export const DELETE = proxyRequest;
