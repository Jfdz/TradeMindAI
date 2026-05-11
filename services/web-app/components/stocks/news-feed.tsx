"use client";

import { useEffect, useState } from "react";
import type { NewsItem } from "@/lib/enrichment/yahoo-news";

type NewsFeedProps = {
  ticker: string;
};

function formatDate(unix: number) {
  return new Date(unix * 1000).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

export function NewsFeed({ ticker }: NewsFeedProps) {
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetch(`/api/stocks/${encodeURIComponent(ticker)}/news`)
      .then((r) => r.json())
      .then((data: NewsItem[]) => { setItems(data); })
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [ticker]);

  if (loading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }, (_, i) => (
          <div key={i} className="h-20 animate-pulse rounded-2xl bg-bg-2" />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border bg-bg-2 px-4 py-6 text-sm text-text-2">
        No news available for {ticker}.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Market news</div>
      <div className="flex gap-3 overflow-x-auto pb-2 scrollbar-none">
        {items.map((item) => (
          <a
            key={item.id}
            href={item.url}
            target="_blank"
            rel="noopener noreferrer"
            className="block min-w-[240px] max-w-[280px] shrink-0 rounded-2xl border border-border bg-bg-2 p-4 transition hover:border-border-strong"
          >
            <div className="line-clamp-3 text-sm text-text-1 leading-6">{item.headline}</div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-text-3">
              <span className="truncate">{item.source}</span>
              {item.datetime ? <span>{formatDate(item.datetime)}</span> : null}
            </div>
          </a>
        ))}
      </div>
    </div>
  );
}
