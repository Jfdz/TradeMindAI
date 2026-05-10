import { tickerQuotes, type TickerQuote } from "@/lib/trademind-content";

const FINNHUB_BASE = "https://finnhub.io/api/v1";

const SYMBOLS: { pair: string; finnhub: string }[] = [
  { pair: "BTC/USDT", finnhub: "BINANCE:BTCUSDT" },
  { pair: "ETH/USDT", finnhub: "BINANCE:ETHUSDT" },
  { pair: "SOL/USDT", finnhub: "BINANCE:SOLUSDT" },
  { pair: "NVDA", finnhub: "NVDA" },
  { pair: "AAPL", finnhub: "AAPL" },
  { pair: "TSLA", finnhub: "TSLA" },
  { pair: "SPY", finnhub: "SPY" },
  { pair: "QQQ", finnhub: "QQQ" },
  { pair: "EUR/USD", finnhub: "OANDA:EUR_USD" },
  { pair: "GOLD", finnhub: "OANDA:XAU_USD" },
];

type FinnhubQuote = {
  c: number; // current price
  d: number; // change
  dp: number; // percent change
  h: number;
  l: number;
  o: number;
  pc: number; // previous close
};

function formatPrice(price: number, pair: string): string {
  if (pair.includes("/") && !pair.includes("BTC") && !pair.includes("ETH") && !pair.includes("SOL")) {
    return price.toFixed(4);
  }
  if (price >= 1000) {
    return price.toLocaleString("en-US", { maximumFractionDigits: 1 });
  }
  return price.toFixed(2);
}

function formatChange(dp: number): string {
  const sign = dp >= 0 ? "+" : "";
  return `${sign}${dp.toFixed(2)}%`;
}

async function fetchQuote(symbol: { pair: string; finnhub: string }, token: string): Promise<TickerQuote> {
  const url = `${FINNHUB_BASE}/quote?symbol=${encodeURIComponent(symbol.finnhub)}&token=${token}`;
  const res = await fetch(url, { next: { revalidate: 60 } });
  if (!res.ok) throw new Error(`Finnhub ${symbol.finnhub}: ${res.status}`);
  const data: FinnhubQuote = await res.json();
  if (!data.c || data.c === 0) throw new Error(`Finnhub ${symbol.finnhub}: no price`);
  return {
    pair: symbol.pair,
    price: formatPrice(data.c, symbol.pair),
    change: formatChange(data.dp),
    positive: data.dp >= 0,
  };
}

export async function fetchMarketQuotes(): Promise<TickerQuote[]> {
  const token = process.env.FINNHUB_API_KEY;
  if (!token) return tickerQuotes;

  try {
    const results = await Promise.all(SYMBOLS.map((s) => fetchQuote(s, token)));
    return results;
  } catch {
    return tickerQuotes;
  }
}
