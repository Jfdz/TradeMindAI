export type NewsItem = {
  id: string;
  headline: string;
  summary: string;
  source: string;
  url: string;
  datetime: number;
  image?: string;
};

type YahooSearchResponse = {
  news?: {
    uuid: string;
    title: string;
    summary?: string;
    publisher: string;
    link: string;
    providerPublishTime: number;
    thumbnail?: { resolutions?: { url: string }[] };
  }[];
};

export async function fetchYahooNews(ticker: string): Promise<NewsItem[]> {
  const url = `https://query1.finance.yahoo.com/v1/finance/search?q=${encodeURIComponent(ticker)}+stock&newsCount=10&lang=en-US`;
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4_000);
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { "User-Agent": "Mozilla/5.0" },
    });
    clearTimeout(timeout);
    if (!res.ok) return [];
    const data = (await res.json()) as YahooSearchResponse;
    return (data.news ?? []).map((item) => ({
      id: item.uuid,
      headline: item.title,
      summary: item.summary ?? "",
      source: item.publisher,
      url: item.link,
      datetime: item.providerPublishTime,
      image: item.thumbnail?.resolutions?.[0]?.url,
    }));
  } catch {
    return [];
  }
}
