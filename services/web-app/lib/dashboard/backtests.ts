import { equityCurve, type EquityPoint } from "@/lib/dashboard/performance";

export const demoEquityCurve: EquityPoint[] = equityCurve;

export const demoDrawdownCurve: EquityPoint[] = [
  { time: "2026-04-07", value: 0 },
  { time: "2026-04-08", value: -0.4 },
  { time: "2026-04-09", value: -0.7 },
  { time: "2026-04-10", value: -0.2 },
  { time: "2026-04-11", value: -0.1 },
  { time: "2026-04-14", value: -0.3 },
  { time: "2026-04-15", value: -0.1 },
  { time: "2026-04-16", value: -0.05 },
  { time: "2026-04-17", value: -0.15 },
  { time: "2026-04-20", value: 0 },
];

export const demoBenchmarkCurve: EquityPoint[] = [
  { time: "2026-04-07", value: 10000 },
  { time: "2026-04-08", value: 10060 },
  { time: "2026-04-09", value: 10010 },
  { time: "2026-04-10", value: 10120 },
  { time: "2026-04-11", value: 10170 },
  { time: "2026-04-14", value: 10210 },
  { time: "2026-04-15", value: 10290 },
  { time: "2026-04-16", value: 10310 },
  { time: "2026-04-17", value: 10380 },
  { time: "2026-04-20", value: 10440 },
];

export function formatMoney(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

export function formatPercent(value: number) {
  const formatted = `${Math.abs(value).toFixed(2)}%`;
  return value >= 0 ? `+${formatted}` : `-${formatted}`;
}
