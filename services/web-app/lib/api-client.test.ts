import { beforeEach, describe, expect, it, vi } from "vitest";

const getSessionMock = vi.fn();

vi.mock("next-auth/react", () => ({
  getSession: getSessionMock,
}));

describe("apiClient", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    getSessionMock.mockReset();
    getSessionMock.mockResolvedValue(null);
  });

  it("sends bearer token when session includes accessToken", async () => {
    getSessionMock.mockResolvedValue({ accessToken: "token-123" });
    const body = JSON.stringify({
      id: "sig-1",
      symbol: "AAPL",
      type: "BUY",
      confidence: 0.9,
      generatedAt: "2026-04-28T10:00:00Z",
      timeframe: "1D",
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        statusText: "OK",
        headers: new Headers({ "content-type": "application/json" }),
        text: async () => body,
        json: async () => JSON.parse(body),
      }),
    );
    const { apiClient } = await import("./api-client");

    await apiClient.getSignal("sig-1");

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8082/api/v1/signals/sig-1",
      expect.objectContaining({
        cache: "no-store",
        headers: expect.objectContaining({
          Accept: "application/json",
          Authorization: "Bearer token-123",
        }),
      }),
    );
  });

  it("surfaces backtest submission failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("backend down")));
    const { apiClient } = await import("./api-client");

    await expect(
      apiClient.submitBacktest({
        symbol: "NVDA",
        from: "2026-04-01",
        to: "2026-04-16",
        quantity: 5,
      }),
    ).rejects.toThrow("backend down");
  });

  it("surfaces current user request failures", async () => {
    getSessionMock.mockResolvedValue({
      user: {
        id: "user-1",
        email: "user@example.com",
        name: "Ada Lovelace",
      },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        statusText: "Internal Server Error",
        headers: new Headers(),
        text: async () => "",
      }),
    );
    const { apiClient } = await import("./api-client");

    await expect(apiClient.getCurrentUser()).rejects.toThrow("Request failed with status 500");
  });

  it("returns null when no demo latest price exists and the request fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    const { apiClient } = await import("./api-client");

    const price = await apiClient.getLatestPrice("UNKNOWN");

    expect(price).toBeNull();
  });

  it("getSignals builds URL with page/size/sort defaults", async () => {
    const pagedBody = JSON.stringify({
      content: [],
      number: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      headers: new Headers({ "content-type": "application/json" }),
      text: async () => pagedBody,
    });
    vi.stubGlobal("fetch", fetchMock);
    const { apiClient } = await import("./api-client");

    await apiClient.getSignals();

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/signals?page=0&size=10&sort=generatedAt,desc"),
      expect.any(Object),
    );
  });

  it("getSignals passes explicit page number in URL", async () => {
    const pagedBody = JSON.stringify({
      content: [],
      number: 2,
      size: 10,
      totalElements: 25,
      totalPages: 3,
      first: false,
      last: false,
    });
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
      headers: new Headers({ "content-type": "application/json" }),
      text: async () => pagedBody,
    });
    vi.stubGlobal("fetch", fetchMock);
    const { apiClient } = await import("./api-client");

    const result = await apiClient.getSignals({ page: 2 });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("page=2"),
      expect.any(Object),
    );
    expect(result.number).toBe(2);
    expect(result.totalPages).toBe(3);
    expect(result.first).toBe(false);
    expect(result.last).toBe(false);
  });

});
