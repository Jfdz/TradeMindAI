"use client";

import { useMemo } from "react";

import { AiDecisionCard } from "@/components/dashboard/ai-decision-card";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

const DAY_MS = 24 * 60 * 60 * 1000;

type Props = {
  readonly signals: readonly FilteredSignal[];
  readonly signalLogos?: Record<string, string | null> | null;
  readonly typeBadgeClass: (type: FilteredSignal["type"]) => string;
};

/**
 * Recent AI decisions (EXECUTION_PLAN.md C2.5). Replaces the static
 * `signals.slice(0, 4)` list with a horizontal scroll-snap strip of every
 * signal generated in the last 24 h that has grounded reasoning news.
 *
 * The 24 h / has-news filter lives here (not in the BFF) so the page data
 * contract stays unchanged and the rule is unit-coverable in isolation.
 */
export function RecentDecisionsCarousel({
  signals,
  signalLogos,
  typeBadgeClass,
}: Props) {
  const recent = useMemo(() => {
    const cutoff = Date.now() - DAY_MS;
    return signals.filter((s) => {
      if (s.reasoningNews == null) return false;
      const ts = new Date(s.generatedAt).getTime();
      return Number.isFinite(ts) && ts >= cutoff;
    });
  }, [signals]);

  if (recent.length === 0) {
    return (
      <div className="mt-6 rounded-[20px] border border-dashed border-border bg-bg-2 px-4 py-6 text-sm text-text-2">
        No AI decisions with news in the last 24 hours.
      </div>
    );
  }

  return (
    <div
      className="mt-6 flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2 [scrollbar-width:thin]"
      role="list"
      aria-label="Recent AI decisions"
    >
      {recent.map((signal) => (
        <div
          key={signal.id}
          role="listitem"
          className="w-[300px] shrink-0 snap-start sm:w-[340px]"
        >
          <AiDecisionCard
            signal={signal}
            logoUrl={signalLogos?.[signal.symbol]}
            typeBadgeClass={typeBadgeClass(signal.type)}
          />
        </div>
      ))}
    </div>
  );
}
