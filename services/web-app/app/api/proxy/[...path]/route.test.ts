import { beforeEach, describe, expect, it, vi } from "vitest";

const getTokenMock = vi.fn();

vi.mock("@clerk/nextjs/server", () => ({
  auth: () => Promise.resolve({ getToken: getTokenMock }),
}));

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

function makeNextRequest(method: string, urlPath: string, body?: string) {
  const url = `http://localhost:3000/${urlPath}`;
  const parsed = new URL(url);
  const req = {
    method,
    nextUrl: parsed,
    headers: new Headers(body ? { "Content-Type": "application/json" } : {}),
    body: body ?? null,
    text: async () => body ?? "",
  };
  return req as unknown as import("next/server").NextRequest;
}

async function getHandlers() {
  const mod = await import("./route");
  return mod;
}

describe("proxy route", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  it("returns 401 when Clerk has no token", async () => {
    getTokenMock.mockResolvedValue(null);
    const { GET } = await getHandlers();

    const req = makeNextRequest("GET", "api/v1/signals");
    const res = await GET(req, { params: Promise.resolve({ path: ["api", "v1", "signals"] }) });

    expect(res.status).toBe(401);
    const json = await res.json();
    expect(json).toMatchObject({ error: "Unauthorized" });
  });

  it("forwards GET request with Bearer token to upstream", async () => {
    getTokenMock.mockResolvedValue("clerk-jwt-token");
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const { GET } = await getHandlers();

    const req = makeNextRequest("GET", "api/v1/signals?page=0");
    const res = await GET(req, { params: Promise.resolve({ path: ["api", "v1", "signals"] }) });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/signals"),
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Authorization: "Bearer clerk-jwt-token" }),
        cache: "no-store",
      }),
    );
    expect(res.status).toBe(200);
  });

  it("passes upstream status codes through unchanged", async () => {
    getTokenMock.mockResolvedValue("tok");
    fetchMock.mockResolvedValue(
      new Response("Not Found", { status: 404, headers: { "Content-Type": "application/json" } }),
    );
    const { GET } = await getHandlers();

    const req = makeNextRequest("GET", "api/v1/signals/missing");
    const res = await GET(req, {
      params: Promise.resolve({ path: ["api", "v1", "signals", "missing"] }),
    });

    expect(res.status).toBe(404);
  });

  it("forwards X- headers from upstream response", async () => {
    getTokenMock.mockResolvedValue("tok");
    fetchMock.mockResolvedValue(
      new Response("{}", {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "X-Correlation-ID": "abc-123",
          "X-RateLimit-Remaining": "42",
        },
      }),
    );
    const { GET } = await getHandlers();

    const req = makeNextRequest("GET", "api/v1/signals");
    const res = await GET(req, { params: Promise.resolve({ path: ["api", "v1", "signals"] }) });

    expect(res.headers.get("x-correlation-id")).toBe("abc-123");
    expect(res.headers.get("x-ratelimit-remaining")).toBe("42");
  });
});
