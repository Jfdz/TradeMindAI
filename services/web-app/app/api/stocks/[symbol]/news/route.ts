import { NextRequest, NextResponse } from "next/server";
import { fetchYahooNews, type NewsItem } from "@/lib/enrichment/yahoo-news";

const FINNHUB_BASE = "https://finnhub.io/api/v1";

type FinnhubNewsItem = {
  id?: number;
  headline?: string;
  summary?: string;
  source?: string;
  url?: string;
  datetime?: number;
  image?: string;
};

async function fetchFinnhubNews(ticker: string, from: string, to: string, apiKey: string): Promise<NewsItem[]> {
  if (!apiKey) return [];
  try {
    const url = `${FINNHUB_BASE}/company-news?symbol=${encodeURIComponent(ticker)}&from=${from}&to=${to}&token=${apiKey}`;
    const res = await fetch(url, { next: { revalidate: 300 } });
    if (!res.ok) return [];
    const data = (await res.json()) as FinnhubNewsItem[];
    if (!Array.isArray(data)) return [];
    return data.map((item) => ({
      id: String(item.id ?? Math.random()),
      headline: item.headline ?? "",
      summary: item.summary ?? "",
      source: item.source ?? "Finnhub",
      url: item.url ?? "",
      datetime: item.datetime ?? 0,
      image: item.image,
    }));
  } catch {
    return [];
  }
}

export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ symbol: string }> }
) {
  const { symbol: ticker } = await params;
  const now = new Date();
  const to = now.toISOString().slice(0, 10);
  const fromDate = new Date(now);
  fromDate.setDate(fromDate.getDate() - 14);
  const from = fromDate.toISOString().slice(0, 10);

  const apiKey = process.env.FINNHUB_API_KEY ?? "";
  let news = await fetchFinnhubNews(ticker, from, to, apiKey);

  if (news.length === 0) {
    news = await fetchYahooNews(ticker);
  }

  return NextResponse.json(news);
}
