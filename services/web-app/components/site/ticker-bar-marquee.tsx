"use client";

import { cn } from "@/lib/utils";
import type { TickerQuote } from "@/lib/trademind-content";

export function TickerBarMarquee({ quotes }: { quotes: TickerQuote[] }) {
  const track = [...quotes, ...quotes];
  return (
    <div className="sticky top-0 z-50 h-9 overflow-hidden border-b border-border bg-bg-0/95 backdrop-blur-[20px]">
      <div className="group flex h-full items-center overflow-hidden">
        <div className="flex min-w-max animate-marquee items-center gap-8 whitespace-nowrap px-5 text-[11px] uppercase tracking-[0.18em] text-text-2 group-hover:[animation-play-state:paused]">
          {track.map((item, index) => (
            <div
              className="flex items-center gap-2"
              key={`${item.pair}-${index}`}
              title={item.date ? `as of ${item.date}` : undefined}
            >
              <span className="h-1.5 w-1.5 rounded-full bg-cyan animate-pulse-soft" />
              <span className="font-mono text-text-1">{item.pair}</span>
              <span className="font-mono text-text-2">{item.price}</span>
              <span className={cn("font-mono", item.positive ? "text-green" : "text-red")}>
                {item.change}
              </span>
            </div>
          ))}
        </div>
        <div className="absolute right-0 h-full w-24 bg-gradient-to-l from-bg-0 to-transparent" />
      </div>
    </div>
  );
}
