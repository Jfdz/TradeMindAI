import type { BusinessDay, SeriesMarker, Time } from "lightweight-charts";

export type SignalType = "BUY" | "SELL" | "HOLD";

export type SignalRecord = {
  id: string;
  symbol: string;
  type: SignalType;
  confidence: number;
  price: number;
  date: string;
  timeframe: string;
  note: string;
  reasoning: string;
  entry: number;
  takeProfit: number;
  stopLoss: number;
  status: "LIVE" | "PENDING";
  age: string;
  live: boolean;
};

export type ChartCandle = {
  time: BusinessDay;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

export type ChartMarker = SeriesMarker<Time>;

export const signalRecords: SignalRecord[] = [
  {
    id: "sig-btc-1",
    symbol: "BTC/USDT",
    type: "BUY",
    confidence: 0.942,
    price: 68412.5,
    date: "2026-04-20T14:30:00Z",
    timeframe: "4H",
    note: "Momentum and breadth turned positive after a clean reclaim of local range highs.",
    reasoning: "Bullish continuation pattern with volume support and improving higher-timeframe trend alignment.",
    entry: 67980,
    takeProfit: 69820,
    stopLoss: 67620,
    status: "LIVE",
    age: "3m ago",
    live: true,
  },
  {
    id: "sig-eth-1",
    symbol: "ETH/USDT",
    type: "BUY",
    confidence: 0.888,
    price: 3328.4,
    date: "2026-04-20T13:00:00Z",
    timeframe: "1H",
    note: "Trend continuation setup with high relative strength and improving volume profile.",
    reasoning: "Higher lows remain intact and the model sees compressed volatility ahead of a breakout attempt.",
    entry: 3311.2,
    takeProfit: 3408.6,
    stopLoss: 3274.1,
    status: "LIVE",
    age: "17m ago",
    live: true,
  },
  {
    id: "sig-aapl-1",
    symbol: "AAPL",
    type: "HOLD",
    confidence: 0.742,
    price: 178.5,
    date: "2026-04-19T15:45:00Z",
    timeframe: "1D",
    note: "Price remains above the moving average stack, but upside momentum is flattening.",
    reasoning: "Hold zone until earnings drift resolves and price confirms above nearby resistance.",
    entry: 177.8,
    takeProfit: 182.4,
    stopLoss: 174.6,
    status: "PENDING",
    age: "1h ago",
    live: false,
  },
  {
    id: "sig-tsla-1",
    symbol: "TSLA",
    type: "SELL",
    confidence: 0.811,
    price: 187.8,
    date: "2026-04-18T16:15:00Z",
    timeframe: "4H",
    note: "The model flags distribution after repeated failures at resistance and weaker breadth.",
    reasoning: "Momentum has rolled over and stop liquidity sits below the last pivot.",
    entry: 188.4,
    takeProfit: 178.1,
    stopLoss: 193.9,
    status: "LIVE",
    age: "24m ago",
    live: true,
  },
  {
    id: "sig-amd-1",
    symbol: "AMD",
    type: "BUY",
    confidence: 0.792,
    price: 171.25,
    date: "2026-04-17T12:10:00Z",
    timeframe: "1D",
    note: "Semiconductor leadership remains intact with improving earnings-revision sentiment.",
    reasoning: "A clean catalyst map and stable relative strength keep the trend constructive.",
    entry: 169.9,
    takeProfit: 177.8,
    stopLoss: 166.2,
    status: "PENDING",
    age: "39m ago",
    live: false,
  },
  {
    id: "sig-msft-1",
    symbol: "MSFT",
    type: "HOLD",
    confidence: 0.691,
    price: 421.1,
    date: "2026-04-16T11:05:00Z",
    timeframe: "1D",
    note: "Trend quality is stable, but the setup lacks a high-conviction entry trigger.",
    reasoning: "Neutral structure with no edge until the next confirmation candle.",
    entry: 418.8,
    takeProfit: 431.5,
    stopLoss: 414.1,
    status: "LIVE",
    age: "2h ago",
    live: true,
  },
];

export function formatConfidence(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

export function formatPrice(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

export function formatSignalDate(value: string) {
  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function getSignalById(signalId: string) {
  return signalRecords.find((signal) => signal.id === signalId);
}

function buildBusinessDay(value: string, dayOffset: number): BusinessDay {
  const base = new Date(value);
  base.setUTCDate(base.getUTCDate() + dayOffset);

  return {
    year: base.getUTCFullYear(),
    month: base.getUTCMonth() + 1,
    day: base.getUTCDate(),
  };
}

export function getSignalChartData(signal: SignalRecord): { candles: ChartCandle[]; marker: ChartMarker } {
  const basePrice = signal.price;
  const candles = Array.from({ length: 12 }, (_, index) => {
    const drift = (index - 5) * 1.6;
    const open = Number((basePrice - 9 + drift).toFixed(2));
    const close = Number((open + (index % 2 === 0 ? 2.4 : -1.5)).toFixed(2));
    const high = Number((Math.max(open, close) + 2.1).toFixed(2));
    const low = Number((Math.min(open, close) - 1.8).toFixed(2));

    return {
      time: buildBusinessDay(signal.date, index - 6),
      open,
      high,
      low,
      close,
      volume: 820000 + index * 94000,
    };
  });

  return {
    candles,
    marker: {
      time: candles[candles.length - 1].time,
      position: signal.type === "SELL" ? "aboveBar" : "belowBar",
      color: signal.type === "SELL" ? "#ff4d6a" : signal.type === "BUY" ? "#00d68f" : "#e8b84b",
      shape: signal.type === "SELL" ? "arrowDown" : signal.type === "BUY" ? "arrowUp" : "circle",
      text: signal.type,
    },
  };
}
