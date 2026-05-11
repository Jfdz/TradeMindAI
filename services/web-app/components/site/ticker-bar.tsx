"use client";

import { useQuery } from "@tanstack/react-query";

import { apiClient, type MarketPriceResponse } from "@/lib/api-client";
import { tickerQuotes, type TickerQuote } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

const TOP_SYMBOLS = ["NVDA", "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "SPY", "QQQ", "NFLX"];

function formatPrice(value: number): string {
  if (value >= 1000) return value.toLocaleString("en-US", { maximumFractionDigits: 1 });
  return value.toFixed(2);
}

function toTickerQuote(price: MarketPriceResponse): TickerQuote {
  const { open, close } = price.ohlcv;
  const pct = open === 0 ? 0 : ((close - open) / open) * 100;
  const sign = pct >= 0 ? "+" : "";
  return {
    pair: price.ticker,
    price: formatPrice(close),
    change: `${sign}${pct.toFixed(2)}%`,
    positive: pct >= 0,
  };
}

export function TickerBar() {
  const { data } = useQuery({
    queryKey: ["ticker-bar", TOP_SYMBOLS],
    queryFn: () => apiClient.getLatestPrices(TOP_SYMBOLS),
    staleTime: 30_000,
    refetchInterval: 60_000,
    placeholderData: (prev) => prev,
  });

  const quotes =
    data?.prices && data.prices.length >= 5 ? data.prices.map(toTickerQuote) : tickerQuotes;
  const track = [...quotes, ...quotes];

  return (
    <div className="sticky top-0 z-50 h-9 overflow-hidden border-b border-border bg-bg-0/95 backdrop-blur-[20px]">
      <div className="group flex h-full items-center overflow-hidden">
        <div className="flex min-w-max animate-marquee items-center gap-8 whitespace-nowrap px-5 text-[11px] uppercase tracking-[0.18em] text-text-2 group-hover:[animation-play-state:paused]">
          {track.map((item, index) => (
            <div className="flex items-center gap-2" key={`${item.pair}-${index}`}>
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
