import { TickerBarMarquee } from "./ticker-bar-marquee";
import type { TickerQuote } from "@/lib/trademind-content";

const MARKET_DATA_BASE_URL =
  process.env.MARKET_DATA_BASE_URL ?? "http://localhost:8081";

const TICKERS = ["AAPL", "TSLA", "NVDA", "MSFT", "GOOGL", "META", "BTC-USD", "ETH-USD"];

const DISPLAY_LABEL: Record<string, string> = {
  "BTC-USD": "BTC/USD",
  "ETH-USD": "ETH/USD",
};

type PriceItem = {
  ticker: string;
  date: string;
  ohlcv: { open: number; close: number };
};

function formatPrice(value: number, ticker: string): string {
  if (ticker === "BTC-USD" || value >= 1000) {
    return value.toLocaleString("en-US", { maximumFractionDigits: 1 });
  }
  return value.toFixed(2);
}

function toTickerQuote(item: PriceItem): TickerQuote {
  const { open, close } = item.ohlcv;
  const pct = open === 0 ? 0 : (close - open) / open;
  const sign = pct >= 0 ? "+" : "";
  return {
    pair: DISPLAY_LABEL[item.ticker] ?? item.ticker,
    price: formatPrice(close, item.ticker),
    change: `${sign}${(pct * 100).toFixed(2)}%`,
    positive: pct >= 0,
    date: item.date,
  };
}

function TickerBarUnavailable() {
  return (
    <div className="sticky top-0 z-50 h-9 overflow-hidden border-b border-border bg-bg-0/95 backdrop-blur-[20px]">
      <div className="flex h-full items-center justify-center">
        <span className="text-[11px] uppercase tracking-[0.18em] text-text-2">
          • Live data temporarily unavailable
        </span>
      </div>
    </div>
  );
}

export async function TickerBar() {
  try {
    const params = TICKERS.map((t) => `tickers=${encodeURIComponent(t)}`).join("&");
    const url = `${MARKET_DATA_BASE_URL}/api/v1/prices/latest?${params}&timeframe=DAILY`;
    const res = await fetch(url, { next: { revalidate: 60 } });

    if (!res.ok) {
      console.error(
        `event=ticker_bar.fetch_failed status=${res.status} tickers=${TICKERS.join(",")}`
      );
      return <TickerBarUnavailable />;
    }

    const data: { prices: PriceItem[] } = await res.json();
    const quotes = data.prices.map(toTickerQuote);

    if (quotes.length === 0) {
      return <TickerBarUnavailable />;
    }

    return <TickerBarMarquee quotes={quotes} />;
  } catch (err) {
    console.error(
      `event=ticker_bar.fetch_failed error=${String(err)} tickers=${TICKERS.join(",")}`
    );
    return <TickerBarUnavailable />;
  }
}
