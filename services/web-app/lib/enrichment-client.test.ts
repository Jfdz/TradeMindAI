import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchAggregatedTickerNews,
  fetchEarnings,
  fetchMarketNews,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
  fetchTickerNews,
  fetchTickerNewsForView,
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

describe("fetchAggregatedTickerNews", () => {
  it("calls the news-aggregated endpoint with from/to/limit", async () => {
    mockFetch.mockResolvedValue(
      okJson([
        {
          id: 42,
          headline: "Aggregated headline",
          publishedAt: "2026-05-12T10:00:00Z",
          image: "https://x/a.png",
        },
      ]),
    );

    const result = await fetchAggregatedTickerNews(
      "AAPL",
      "2026-05-01T00:00:00Z",
      "2026-05-31T00:00:00Z",
      15,
      "tok-1",
    );

    expect(result).toHaveLength(1);
    expect(result[0].headline).toBe("Aggregated headline");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/enrichment/news-aggregated/AAPL"),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer tok-1" }),
      }),
    );
    const calledWith = mockFetch.mock.calls[0][0] as string;
    expect(calledWith).toContain("limit=15");
    expect(calledWith).toContain("from=2026-05-01T00");
    expect(calledWith).toContain("to=2026-05-31T00");
  });

  it("returns empty array on 5xx", async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503 });

    const result = await fetchAggregatedTickerNews(
      "AAPL",
      "2026-05-01T00:00:00Z",
      "2026-05-31T00:00:00Z",
    );

    expect(result).toEqual([]);
  });
});

describe("fetchTickerNewsForView", () => {
  const originalFlag = process.env.USE_AGGREGATED_NEWS;

  afterEach(() => {
    if (originalFlag === undefined) {
      delete process.env.USE_AGGREGATED_NEWS;
    } else {
      process.env.USE_AGGREGATED_NEWS = originalFlag;
    }
  });

  it("hits the aggregated endpoint when USE_AGGREGATED_NEWS=true", async () => {
    process.env.USE_AGGREGATED_NEWS = "true";
    mockFetch.mockResolvedValue(okJson([{ id: 1, headline: "Agg", publishedAt: "2026-05-12T10:00:00Z" }]));

    await fetchTickerNewsForView("AAPL", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 5);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/enrichment/news-aggregated/AAPL"),
      expect.anything(),
    );
  });

  it("hits the single-provider endpoint when the flag is off", async () => {
    delete process.env.USE_AGGREGATED_NEWS;
    mockFetch.mockResolvedValue(okJson([{ id: 1, headline: "Single", publishedAt: "2026-05-12T10:00:00Z" }]));

    await fetchTickerNewsForView("AAPL", "2026-05-01T00:00:00Z", "2026-05-31T00:00:00Z", 5);

    const calledUrl = mockFetch.mock.calls[0][0] as string;
    expect(calledUrl).toContain("/api/v1/enrichment/news/AAPL");
    expect(calledUrl).not.toContain("news-aggregated");
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
