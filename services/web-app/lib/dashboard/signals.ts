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
    id: "sig-aapl-1",
    symbol: "AAPL",
    type: "BUY",
    confidence: 0.92,
    price: 178.5,
    date: "2026-04-20T14:30:00Z",
    timeframe: "1D",
    note: "Momentum and breadth turned positive after three sessions of accumulation.",
  },
  {
    id: "sig-nvda-1",
    symbol: "NVDA",
    type: "BUY",
    confidence: 0.88,
    price: 846.2,
    date: "2026-04-20T13:00:00Z",
    timeframe: "4H",
    note: "Trend continuation setup with high relative strength and improving volume profile.",
  },
  {
    id: "sig-msft-1",
    symbol: "MSFT",
    type: "HOLD",
    confidence: 0.74,
    price: 421.1,
    date: "2026-04-19T15:45:00Z",
    timeframe: "1D",
    note: "Price remains above the moving average stack, but upside momentum is flattening.",
  },
  {
    id: "sig-tsla-1",
    symbol: "TSLA",
    type: "SELL",
    confidence: 0.81,
    price: 187.8,
    date: "2026-04-18T16:15:00Z",
    timeframe: "4H",
    note: "The model flags distribution after repeated failures at resistance and weaker breadth.",
  },
  {
    id: "sig-amd-1",
    symbol: "AMD",
    type: "BUY",
    confidence: 0.79,
    price: 171.25,
    date: "2026-04-17T12:10:00Z",
    timeframe: "1D",
    note: "Semiconductor leadership remains intact with improving earnings-revision sentiment.",
  },
  {
    id: "sig-meta-1",
    symbol: "META",
    type: "HOLD",
    confidence: 0.69,
    price: 512.3,
    date: "2026-04-16T11:05:00Z",
    timeframe: "1D",
    note: "Trend quality is stable, but the setup lacks a high-conviction entry trigger.",
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
  const candles = Array.from({ length: 8 }, (_, index) => {
    const drift = (index - 3) * 1.4;
    const open = Number((basePrice - 5 + drift).toFixed(2));
    const close = Number((open + (index % 2 === 0 ? 2.2 : -1.1)).toFixed(2));
    const high = Number((Math.max(open, close) + 1.8).toFixed(2));
    const low = Number((Math.min(open, close) - 1.5).toFixed(2));

    return {
      time: buildBusinessDay(signal.date, index - 4),
      open,
      high,
      low,
      close,
      volume: 800000 + index * 95000,
    };
  });

  return {
    candles,
    marker: {
      time: candles[candles.length - 1].time,
      position: signal.type === "SELL" ? "aboveBar" : "belowBar",
      color: signal.type === "SELL" ? "#fb7185" : signal.type === "BUY" ? "#34d399" : "#facc15",
      shape: signal.type === "SELL" ? "arrowDown" : signal.type === "BUY" ? "arrowUp" : "circle",
      text: signal.type,
    },
  };
}
