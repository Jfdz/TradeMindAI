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
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          id: "sig-1",
          symbol: "AAPL",
          type: "BUY",
          confidence: 0.9,
          generatedAt: "2026-04-28T10:00:00Z",
          timeframe: "1D",
        }),
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
});
