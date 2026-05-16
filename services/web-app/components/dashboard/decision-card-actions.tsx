"use client";

import { cn } from "@/lib/utils";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

/**
 * Shared card footer actions (EXECUTION_PLAN.md C5.1): the BUY/SELL
 * direction pill and the "Open article" link sit side-by-side on the
 * left in a single row. Single source of truth so `AiDecisionCard` and
 * `RecentDecisionsCarousel` cannot drift.
 *
 * Tap targets are `h-8` (≥ 32 px). Colour conventions are unchanged:
 * emerald tint for BUY, rose for SELL, amber for HOLD.
 */

function directionClass(type: FilteredSignal["type"]): string {
  switch (type) {
    case "BUY":
      return "bg-emerald-500/15 text-emerald-300";
    case "SELL":
      return "bg-rose-500/15 text-rose-300";
    default:
      return "bg-amber-500/15 text-amber-300";
  }
}

type Props = {
  signal: FilteredSignal;
  newsUrl?: string | null;
  articleLabel?: string;
};

export function DecisionCardActions({ signal, newsUrl, articleLabel }: Props) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={cn(
          "inline-flex h-8 items-center rounded-full px-3 text-[10px] font-semibold uppercase tracking-[0.18em]",
          directionClass(signal.type),
        )}
      >
        {signal.type}
      </span>
      {newsUrl && (
        <a
          href={newsUrl}
          target="_blank"
          rel="noopener noreferrer"
          onClick={(e) => e.stopPropagation()}
          className="inline-flex h-8 items-center rounded-full bg-cyan/15 px-3 text-[10px] uppercase tracking-[0.18em] text-cyan hover:text-cyan-bright"
        >
          {articleLabel ?? "Open article"} ↗
        </a>
      )}
    </div>
  );
}
