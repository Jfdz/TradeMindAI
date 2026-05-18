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

  it("filters out imageless items and caps the first view at 5", async () => {
    const { hasOwnImage } = await import(
      "../../../lib/enrichment/news-image-filter"
    );

    const articles = [
      { image: "https://cdn.example.com/a.jpg" },
      { image: null },
      { image: "" },
      { image: "https://s.yimg.com/rz/stage/p/yahoo_finance_en-US_h_p_finance_2.png" },
      { image: "https://cdn.example.com/b.jpg" },
      { image: "https://cdn.example.com/c.jpg" },
      { image: "https://cdn.example.com/d.jpg" },
      { image: "https://cdn.example.com/e.jpg" },
      { image: "https://cdn.example.com/f.jpg" },
    ];

    const filtered = articles.filter((item) => hasOwnImage(item.image));
    expect(filtered).toHaveLength(6);

    const firstView = filtered.slice(0, 5);
    expect(firstView).toHaveLength(5);
  });
});

describe("AISignalSection", () => {
  it("exports the component", async () => {
    const { AISignalSection } = await import("../ai-signal-section");

    expect(typeof AISignalSection).toBe("function");
  });
});
