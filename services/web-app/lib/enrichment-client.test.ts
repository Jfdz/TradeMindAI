import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchEarnings,
  fetchMarketNews,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
  fetchTickerNews,
} from "./enrichment-client";

const mockFetch = vi.fn();

beforeEach(() => {
  vi.stubGlobal("fetch", mockFetch);
  mockFetch.mockReset();
});

function okJson(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: async () => data,
  });
}

function notFound() {
  return Promise.resolve({ ok: false, status: 404 });
}

describe("fetchProfile", () => {
  it("returns profile on 200", async () => {
    mockFetch.mockResolvedValue(okJson({ ticker: "AAPL", name: "Apple Inc.", logo: null }));

    const result = await fetchProfile("AAPL", "tok-1");

    expect(result).not.toBeNull();
    expect(result?.ticker).toBe("AAPL");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/enrichment/profile/AAPL"),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer tok-1" }),
      }),
    );
  });

  it("returns null on 4xx", async () => {
    mockFetch.mockResolvedValue(notFound());

    const result = await fetchProfile("UNKNOWN");

    expect(result).toBeNull();
  });
});

describe("fetchMarketNews", () => {
  it("returns news list on 200", async () => {
    const items = [{ id: 1, headline: "Rate cut", publishedAt: "2026-05-01T10:00:00Z" }];
    mockFetch.mockResolvedValue(okJson(items));

    const result = await fetchMarketNews("general", 5, "tok-1");

    expect(result).toHaveLength(1);
    expect(result[0].headline).toBe("Rate cut");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("category=general"),
      expect.anything(),
    );
  });

  it("returns empty array on 5xx", async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 });

    const result = await fetchMarketNews();

    expect(result).toEqual([]);
  });
});

describe("fetchTickerNews", () => {
  it("passes from/to/limit params", async () => {
    mockFetch.mockResolvedValue(okJson([{ id: 2, headline: "AAPL beats", publishedAt: "2026-04-25T00:00:00Z" }]));

    const result = await fetchTickerNews("AAPL", "2026-04-01T00:00:00Z", "2026-05-01T00:00:00Z", 10);

    expect(result).toHaveLength(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/enrichment/news/AAPL"),
      expect.anything(),
    );
  });
});

describe("fetchEarnings", () => {
  it("returns earnings list on 200", async () => {
    const events = [{ ticker: "AAPL", period: "2026-03-31", year: 2026, quarter: 1, epsActual: 1.52 }];
    mockFetch.mockResolvedValue(okJson(events));

    const result = await fetchEarnings("AAPL", "tok-1");

    expect(result).toHaveLength(1);
    expect(result[0].epsActual).toBe(1.52);
  });
});

describe("fetchRecommendations", () => {
  it("returns recommendations list on 200", async () => {
    const recs = [{ ticker: "AAPL", period: "2026-05-01", buy: 20, hold: 5, sell: 2, strongBuy: 10, strongSell: 1 }];
    mockFetch.mockResolvedValue(okJson(recs));

    const result = await fetchRecommendations("AAPL");

    expect(result[0].buy).toBe(20);
  });
});

describe("fetchPeers", () => {
  it("returns peer tickers on 200", async () => {
    mockFetch.mockResolvedValue(okJson(["MSFT", "GOOGL", "META"]));

    const result = await fetchPeers("AAPL");

    expect(result).toEqual(["MSFT", "GOOGL", "META"]);
  });

  it("returns empty array on 4xx", async () => {
    mockFetch.mockResolvedValue(notFound());

    const result = await fetchPeers("UNKNOWN");

    expect(result).toEqual([]);
  });
});
