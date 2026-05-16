"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { DecisionCardActions } from "@/components/dashboard/decision-card-actions";
import { StockLogo } from "@/components/ui/stock-logo";
import { formatAge } from "@/lib/dashboard/signal-derivation";
import { formatConfidence } from "@/lib/signal-utils";
import { scrubReasoningText } from "@/lib/signal-reasoning-format";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";
import { cn } from "@/lib/utils";

type Props = {
  signal: FilteredSignal;
  logoUrl: string | null | undefined;
  typeBadgeClass: string;
};

function shortHost(url: string | null | undefined): string {
  if (!url) return "";
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return "";
  }
}

function sourceLabel(
  news: NonNullable<FilteredSignal["reasoningNews"]>,
): string {
  const name = news.source?.trim() || shortHost(news.url);
  const when = news.publishedAt ? formatAge(news.publishedAt) : "";
  if (name && when) return `${name} · ${when}`;
  return name || when;
}

/**
 * Recent-AI-decisions card. When the signal's grounded reasoning carries a
 * news image (Track C `reasoning_facts_snapshot.news[0].image`), the card
 * renders with that image as full background + dark gradient overlay so
 * the headline and signal metadata stay readable. Two click targets:
 *
 *   - Card body → navigates to `/dashboard/signals/{id}`
 *   - "Read article ↗" link → opens the upstream news URL in a new tab
 *     (uses stopPropagation so the card click does not fire)
 *
 * When no image is available, or when the <Image> errors out, the card
 * degrades to the original solid-background layout. This way no signal
 * ever falls below the previous visual quality.
 */
export function AiDecisionCard({ signal, logoUrl, typeBadgeClass }: Props) {
  const router = useRouter();
  const [imageBroken, setImageBroken] = useState(false);
  const news = signal.reasoningNews ?? null;
  const hasImage = !!news?.imageUrl && !imageBroken;

  const handleNavigate = () => router.push(`/dashboard/signals/${signal.id}`);

  if (!hasImage) {
    return (
      <div
        role="link"
        tabIndex={0}
        onClick={handleNavigate}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            handleNavigate();
          }
        }}
        className="block cursor-pointer rounded-[20px] border border-border bg-bg-2 p-4 transition hover:border-border-strong hover:bg-bg-3"
      >
        <DefaultBody
          signal={signal}
          logoUrl={logoUrl}
          typeBadgeClass={typeBadgeClass}
        />
        {news?.headline && (
          <NewsFooter signal={signal} news={news} />
        )}
      </div>
    );
  }

  return (
    <div
      role="link"
      tabIndex={0}
      onClick={handleNavigate}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          handleNavigate();
        }
      }}
      className="group relative block cursor-pointer overflow-hidden rounded-[20px] border border-border transition hover:border-border-strong"
    >
      <div className="relative h-44 w-full bg-bg-2">
        {/* eslint-disable-next-line @next/next/no-img-element -- external untrusted host */}
        <img
          src={news!.imageUrl!}
          alt=""
          loading="lazy"
          onError={() => setImageBroken(true)}
          className="h-full w-full object-cover transition group-hover:scale-[1.02]"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-bg-0 via-bg-0/70 to-transparent" />
        <div className="absolute inset-x-0 top-0 flex items-start justify-between gap-3 p-3">
          <div className="flex items-center gap-2 rounded-full bg-bg-0/70 px-2 py-1 backdrop-blur">
            <StockLogo ticker={signal.symbol} logoUrl={logoUrl} size={20} />
            <span className="font-display text-sm font-semibold text-white">{signal.symbol}</span>
          </div>
          <span
            className={cn(
              "rounded-full border bg-bg-0/70 px-3 py-1 text-[10px] uppercase tracking-[0.22em] backdrop-blur",
              typeBadgeClass,
            )}
          >
            {signal.type}
          </span>
        </div>
      </div>

      <div className="space-y-3 bg-bg-1 p-4">
        {news!.headline && (
          <h4 className="line-clamp-2 font-display text-sm font-medium leading-snug text-white">
            {news!.headline}
          </h4>
        )}
        {signal.reasoning && (
          <p className="line-clamp-2 text-xs leading-relaxed text-text-2">
            {scrubReasoningText(signal.reasoning, signal)}
          </p>
        )}
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-text-2">
          <div className="flex items-center gap-3">
            <span className="uppercase tracking-[0.22em] text-text-3">{signal.timeframe} · {signal.age}</span>
            <span className="font-mono text-text-1">{formatConfidence(signal.confidence)}</span>
          </div>
          {news!.url && (
            <a
              href={news!.url}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => e.stopPropagation()}
              className="rounded-full bg-cyan/15 px-2.5 py-1 text-[10px] uppercase tracking-[0.18em] text-cyan hover:text-cyan-bright"
            >
              {sourceLabel(news!)} ↗
            </a>
          )}
        </div>
      </div>
    </div>
  );
}

function DefaultBody({ signal, logoUrl, typeBadgeClass }: Props) {
  return (
    <>
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <StockLogo ticker={signal.symbol} logoUrl={logoUrl} size={28} />
          <div>
            <div className="font-display text-lg font-semibold tracking-[-0.03em] text-white">
              {signal.symbol}
            </div>
            <div className="mt-1 text-xs uppercase tracking-[0.22em] text-text-3">
              {signal.timeframe} · {signal.age}
            </div>
          </div>
        </div>
        <div
          className={cn(
            "rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.22em]",
            typeBadgeClass,
          )}
        >
          {signal.type}
        </div>
      </div>
      <div className="mt-4 grid gap-3 text-sm text-text-2 sm:grid-cols-2">
        <div>
          <span className="text-text-3">Confidence</span>
          <div className="mt-1 font-mono text-white">{formatConfidence(signal.confidence)}</div>
        </div>
        <div>
          <span className="text-text-3">Reasoning</span>
          <div className="mt-1 line-clamp-2 text-text-1">
            {scrubReasoningText(signal.reasoning, signal)}
          </div>
        </div>
      </div>
    </>
  );
}

function NewsFooter({
  signal,
  news,
}: Readonly<{
  signal: FilteredSignal;
  news: NonNullable<FilteredSignal["reasoningNews"]>;
}>) {
  // C5.2 — never render an orphan "Grounded in" with nothing under it.
  if (!news.url && !news.headline) return null;
  return (
    <div className="mt-3 space-y-2 border-t border-border pt-3 text-xs">
      {news.headline && (
        <p className="line-clamp-2 text-text-2">
          <span className="uppercase tracking-[0.18em] text-text-3">Grounded in </span>
          <span className="text-text-1">{news.headline}</span>
        </p>
      )}
      {/* C5.1 — direction pill + article link grouped on the left. */}
      <DecisionCardActions
        signal={signal}
        newsUrl={news.url}
        articleLabel={sourceLabel(news)}
      />
    </div>
  );
}
