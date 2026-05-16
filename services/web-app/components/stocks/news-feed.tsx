"use client";

import { useEffect, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import Image from "next/image";
import type { NewsItemResponse } from "@/lib/enrichment-client";
import { hasOwnImage } from "@/lib/enrichment/news-image-filter";

const PAGE_SIZE = 5;
const CARD_CHROME =
  "rounded-xl border border-cyan-500/30 bg-card shadow-[0_0_20px_rgba(6,182,212,0.08)]";

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
  const [hasLoadedMore, setHasLoadedMore] = useState(false);
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, status } =
    useInfiniteQuery({
      queryKey: ["news", ticker],
      queryFn: ({ pageParam }) => fetchNewsPage(ticker, pageParam as number),
      getNextPageParam: (_last, pages) => pages.length,
      initialPageParam: 0,
    });

  const articles = data?.pages.flat() ?? [];
  const filtered = articles.filter((item) => hasOwnImage(item.image));
  const visible = filtered.slice(0, visibleCount);
  const canLoadMore = visibleCount < filtered.length || (hasNextPage ?? false);

  // The image filter shrinks each fetched week; a week can yield < PAGE_SIZE
  // usable items. Keep pulling older weeks until we have enough to satisfy the
  // current reveal target (or pages run out). Runs on mount and after each
  // "Load more" so the button never dead-clicks.
  useEffect(() => {
    if (
      filtered.length < visibleCount &&
      hasNextPage &&
      !isFetchingNextPage
    ) {
      void fetchNextPage();
    }
  }, [filtered.length, visibleCount, hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (status === "pending") {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={`news-skeleton-${i}`}
            className={`h-24 animate-pulse ${CARD_CHROME}`}
          />
        ))}
      </div>
    );
  }

  if (status === "error" || articles.length === 0) {
    return (
      <div className={`${CARD_CHROME} p-6 text-center text-sm text-muted-foreground`}>
        No news available.
      </div>
    );
  }

  if (filtered.length === 0 && !hasNextPage && !isFetchingNextPage) {
    return (
      <div className={`${CARD_CHROME} p-6 text-center text-sm text-muted-foreground`}>
        No news with images available.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {visible.map((item) => (
        <a
          key={item.id}
          href={item.url ?? "#"}
          target="_blank"
          rel="noopener noreferrer"
          className={`${CARD_CHROME} flex gap-3 p-3 transition-colors hover:border-cyan-400/60 hover:bg-accent/50`}
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
      {canLoadMore && !hasLoadedMore && (
        <button
          onClick={() => { setVisibleCount((count) => count + PAGE_SIZE); setHasLoadedMore(true); }}
          disabled={isFetchingNextPage}
          className={`${CARD_CHROME} w-full py-2 text-sm text-muted-foreground transition-colors hover:border-cyan-400/60 hover:bg-accent/50 disabled:opacity-50`}
        >
          {isFetchingNextPage ? "Loading…" : "Load more"}
        </button>
      )}
    </div>
  );
}
