"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Image from "next/image";
import type { NewsItemResponse } from "@/lib/enrichment-client";

type Props = {
  readonly ticker: string;
};

export async function fetchNewsPage(
  ticker: string,
  weeksAgo: number,
): Promise<NewsItemResponse[]> {
  const res = await fetch(`/api/stocks/${ticker}/news?weeksAgo=${weeksAgo}`);
  if (!res.ok) return [];
  return res.json() as Promise<NewsItemResponse[]>;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function NewsFeed({ ticker }: Props) {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, status } =
    useInfiniteQuery({
      queryKey: ["news", ticker],
      queryFn: ({ pageParam }) => fetchNewsPage(ticker, pageParam as number),
      getNextPageParam: (_last, pages) => pages.length,
      initialPageParam: 0,
    });

  const articles = data?.pages.flat() ?? [];

  if (status === "pending") {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={`news-skeleton-${i}`}
            className="h-24 animate-pulse rounded-xl bg-card"
          />
        ))}
      </div>
    );
  }

  if (status === "error" || articles.length === 0) {
    return (
      <div className="rounded-xl border bg-card p-6 text-center text-sm text-muted-foreground">
        No news available.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {articles.map((item) => (
        <a
          key={item.id}
          href={item.url ?? "#"}
          target="_blank"
          rel="noopener noreferrer"
          className="flex gap-3 rounded-xl border bg-card p-3 hover:bg-accent/50 transition-colors"
        >
          {item.image && (
            <div className="relative hidden sm:block h-16 w-16 flex-shrink-0 overflow-hidden rounded-lg">
              <Image
                src={item.image}
                alt={item.headline}
                fill
                className="object-cover"
                sizes="64px"
                unoptimized
              />
            </div>
          )}
          <div className="min-w-0 flex-1 space-y-1">
            <p className="text-sm font-medium leading-snug line-clamp-2">
              {item.headline}
            </p>
            <p className="text-xs text-muted-foreground">
              {item.source ?? "Unknown"} · {formatDate(item.publishedAt)}
            </p>
          </div>
        </a>
      ))}
      {hasNextPage && (
        <button
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="w-full rounded-xl border bg-card py-2 text-sm text-muted-foreground hover:bg-accent/50 transition-colors disabled:opacity-50"
        >
          {isFetchingNextPage ? "Loading…" : "Load more"}
        </button>
      )}
    </div>
  );
}
