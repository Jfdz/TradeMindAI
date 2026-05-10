import { tickerQuotes, type TickerQuote } from "@/lib/trademind-content";

const FINNHUB_BASE = "https://finnhub.io/api/v1";

const SYMBOLS: { pair: string; finnhub: string }[] = [
  // Crypto
  { pair: "BTC/USDT",  finnhub: "BINANCE:BTCUSDT" },
  { pair: "ETH/USDT",  finnhub: "BINANCE:ETHUSDT" },
  { pair: "SOL/USDT",  finnhub: "BINANCE:SOLUSDT" },
  // AI leaders
  { pair: "NVDA",      finnhub: "NVDA" },
  { pair: "MSFT",      finnhub: "MSFT" },
  { pair: "GOOGL",     finnhub: "GOOGL" },
  { pair: "META",      finnhub: "META" },
  { pair: "AMZN",      finnhub: "AMZN" },
  { pair: "AMD",       finnhub: "AMD" },
  { pair: "AVGO",      finnhub: "AVGO" },
  { pair: "ORCL",      finnhub: "ORCL" },
  { pair: "CRM",       finnhub: "CRM" },
  { pair: "PLTR",      finnhub: "PLTR" },
  { pair: "NOW",       finnhub: "NOW" },
  { pair: "IBM",       finnhub: "IBM" },
  { pair: "QCOM",      finnhub: "QCOM" },
  // S&P 500 top by market cap
  { pair: "AAPL",      finnhub: "AAPL" },
  { pair: "TSLA",      finnhub: "TSLA" },
  { pair: "BRK.B",     finnhub: "BRK.B" },
  { pair: "JPM",       finnhub: "JPM" },
  { pair: "LLY",       finnhub: "LLY" },
  { pair: "V",         finnhub: "V" },
  { pair: "XOM",       finnhub: "XOM" },
  { pair: "MA",        finnhub: "MA" },
  { pair: "COST",      finnhub: "COST" },
  { pair: "HD",        finnhub: "HD" },
  { pair: "PG",        finnhub: "PG" },
  { pair: "NFLX",      finnhub: "NFLX" },
  { pair: "JNJ",       finnhub: "JNJ" },
  { pair: "BAC",       finnhub: "BAC" },
  { pair: "WMT",       finnhub: "WMT" },
  { pair: "MRK",       finnhub: "MRK" },
  { pair: "KO",        finnhub: "KO" },
  { pair: "ABBV",      finnhub: "ABBV" },
  { pair: "GE",        finnhub: "GE" },
  { pair: "UNH",       finnhub: "UNH" },
  { pair: "TXN",       finnhub: "TXN" },
  { pair: "PFE",       finnhub: "PFE" },
  { pair: "RTX",       finnhub: "RTX" },
  { pair: "CAT",       finnhub: "CAT" },
  { pair: "AXP",       finnhub: "AXP" },
  { pair: "GS",        finnhub: "GS" },
  { pair: "BKNG",      finnhub: "BKNG" },
  { pair: "MU",        finnhub: "MU" },
  { pair: "UBER",      finnhub: "UBER" },
  // ETFs
  { pair: "SPY",       finnhub: "SPY" },
  { pair: "QQQ",       finnhub: "QQQ" },
  // Forex & commodities
  { pair: "EUR/USD",   finnhub: "OANDA:EUR_USD" },
  { pair: "GOLD",      finnhub: "OANDA:XAU_USD" },
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

  const settled = await Promise.allSettled(SYMBOLS.map((s) => fetchQuote(s, token)));
  const results = settled
    .filter((r): r is PromiseFulfilledResult<TickerQuote> => r.status === "fulfilled")
    .map((r) => r.value);
  return results.length >= 5 ? results : tickerQuotes;
}
