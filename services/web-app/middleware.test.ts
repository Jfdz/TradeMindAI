import { beforeEach, describe, expect, it, vi } from "vitest";

const getTokenMock = vi.fn();

vi.mock("next-auth/jwt", () => ({
  getToken: getTokenMock,
}));

describe("middleware", () => {
  beforeEach(() => {
    getTokenMock.mockReset();
  });

  it("redirects authenticated users away from public auth pages", async () => {
    getTokenMock.mockResolvedValue({ sub: "user-1" });
    const { middleware } = await import("./middleware");

    const response = await middleware(requestFor("http://localhost:3000/auth/login"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("http://localhost:3000/dashboard");
  });

  it("redirects unauthenticated dashboard requests to login with callback", async () => {
    getTokenMock.mockResolvedValue(null);
    const { middleware } = await import("./middleware");

    const response = await middleware(requestFor("http://localhost:3000/dashboard/backtests"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "http://localhost:3000/auth/login?callbackUrl=%2Fdashboard%2Fbacktests",
    );
  });

  it("allows authenticated dashboard requests through", async () => {
    getTokenMock.mockResolvedValue({ sub: "user-1" });
    const { middleware } = await import("./middleware");

    const response = await middleware(requestFor("http://localhost:3000/dashboard"));

    expect(response.status).toBe(200);
    expect(response.headers.get("location")).toBeNull();
  });
});

function requestFor(url: string) {
  const nextUrl = new URL(url);
  return {
    url,
    nextUrl,
  } as never;
}
