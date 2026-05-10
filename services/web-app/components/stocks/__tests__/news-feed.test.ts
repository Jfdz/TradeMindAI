import { describe, expect, it, vi, afterEach } from "vitest";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchNewsPage", () => {
  it("returns articles on success", async () => {
    const { fetchNewsPage } = await import("../news-feed");

    const articles = [
      { id: 1, headline: "AAPL hits record", publishedAt: "2026-05-01T10:00:00Z", source: "Reuters", url: null, image: null, category: null, summary: null },
    ];
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => articles,
    }));

    const result = await fetchNewsPage("AAPL", 0);

    expect(result).toEqual(articles);
  });

  it("returns empty array on non-ok response", async () => {
    const { fetchNewsPage } = await import("../news-feed");

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));

    const result = await fetchNewsPage("AAPL", 0);

    expect(result).toEqual([]);
  });

  it("calls correct URL with weeksAgo param", async () => {
    const { fetchNewsPage } = await import("../news-feed");

    const mockFetch = vi.fn().mockResolvedValue({ ok: true, json: async () => [] });
    vi.stubGlobal("fetch", mockFetch);

    await fetchNewsPage("TSLA", 3);

    expect(mockFetch).toHaveBeenCalledWith("/api/stocks/TSLA/news?weeksAgo=3");
  });
});

describe("NewsFeed", () => {
  it("exports the component", async () => {
    const { NewsFeed } = await import("../news-feed");

    expect(typeof NewsFeed).toBe("function");
  });
});

describe("AISignalSection", () => {
  it("exports the component", async () => {
    const { AISignalSection } = await import("../ai-signal-section");

    expect(typeof AISignalSection).toBe("function");
  });
});
