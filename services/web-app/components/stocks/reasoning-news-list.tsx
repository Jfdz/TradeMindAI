"use client";

import { useQuery } from "@tanstack/react-query";

import { apiClient } from "@/lib/api-client";

type Props = {
  readonly ticker: string;
};

/**
 * Reasoning sources (EXECUTION_PLAN.md C2.4). Surfaces the news the AI
 * reasoning actually grounded itself on for this ticker's latest signal,
 * next to the broader enrichment `NewsFeed`.
 *
 * Data path note: trading-core's signal contract currently exposes only
 * the first grounded item (`reasoning_facts_snapshot.news[0]` →
 * `reasoningNews`), not the full `news[]` array. Rendering the full array
 * via a dedicated `/api/signals/[id]/news` BFF route requires a
 * trading-core contract change (new field on SignalResponse) and is
 * flagged as human follow-up — see the PR description. Until then this
 * uses the existing JWT-authenticated signal read, which is the correct
 * auth boundary for a customer-facing read (api.md §6).
 */
export function ReasoningNewsList({ ticker }: Props) {
  const { data, status } = useQuery({
    queryKey: ["reasoning-news", ticker],
    queryFn: async () => {
      const response = await apiClient.getSignals();
      return response.content.filter((s) => s.symbol === ticker);
    },
  });

  if (status === "pending") {
    return (
      <div className="rounded-xl border border-border bg-card p-4">
        <div className="h-4 w-40 animate-pulse rounded-full bg-muted" />
        <div className="mt-3 h-12 animate-pulse rounded-lg bg-muted" />
      </div>
    );
  }

  const signal = data?.[0] ?? null;
  const news = signal?.reasoningNews ?? null;

  if (!news || (!news.headline && !news.url)) {
    return null;
  }

  const sourceLine = [news.source, news.publishedAt ? new Date(news.publishedAt).toLocaleDateString() : null]
    .filter(Boolean)
    .join(" · ");

  return (
    <section className="space-y-2">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        Reasoning sources
      </h2>
      <div className="rounded-xl border border-cyan-500/30 bg-card p-4">
        <p className="text-[10px] uppercase tracking-[0.18em] text-muted-foreground">
          Grounded the latest {ticker} signal
        </p>
        <p className="mt-2 text-sm font-medium text-foreground">
          {news.headline ?? "Referenced article"}
        </p>
        {sourceLine && (
          <p className="mt-1 text-xs text-muted-foreground">{sourceLine}</p>
        )}
        {news.url && (
          <a
            href={news.url}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-3 inline-flex text-xs font-semibold text-cyan-400 hover:text-cyan-300"
          >
            Open article ↗
          </a>
        )}
      </div>
    </section>
  );
}
