"use client";

import { useMemo, useState } from "react";

import { CandlestickChart } from "@/components/charts/CandlestickChart";
import { cn } from "@/lib/utils";
import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";

const timeframes = ["15m", "1H", "4H", "1D", "1W"] as const;

function buildSeries(base: number, drift: number): { candles: ChartCandle[]; marker: ChartMarker } {
  const candles = Array.from({ length: 18 }, (_, index) => {
    const trend = base + (index - 8) * drift;
    const open = Number((trend - (index % 2 === 0 ? 1.6 : 0.8)).toFixed(2));
    const close = Number((trend + (index % 3 === 0 ? 1.9 : -0.6)).toFixed(2));
    const high = Number((Math.max(open, close) + 1.4).toFixed(2));
    const low = Number((Math.min(open, close) - 1.5).toFixed(2));

    return {
      time: {
        year: 2026,
        month: 4,
        day: 3 + index,
      },
      open,
      high,
      low,
      close,
      volume: 1_100_000 + index * 75_000,
    };
  });

  return {
    candles,
    marker: {
      time: candles[candles.length - 1].time,
      position: "belowBar",
      color: "#00c8d4",
      shape: "arrowUp",
      text: "BUY",
    },
  };
}

const frameSeries = {
  "15m": buildSeries(68390, 0.7),
  "1H": buildSeries(68410, 1.2),
  "4H": buildSeries(68480, 1.9),
  "1D": buildSeries(68540, 2.3),
  "1W": buildSeries(68620, 3.1),
} satisfies Record<(typeof timeframes)[number], { candles: ChartCandle[]; marker: ChartMarker }>;

export function HeroChartPanel() {
  const [selectedFrame, setSelectedFrame] = useState<(typeof timeframes)[number]>("4H");
  const data = useMemo(() => frameSeries[selectedFrame], [selectedFrame]);
  const markers = useMemo(() => [data.marker], [data.marker]);

  return (
    <div className="overflow-hidden rounded-[28px] border border-border bg-[linear-gradient(180deg,rgba(17,23,32,0.96),rgba(12,16,24,0.95))] shadow-glow">
      <div className="flex flex-col gap-4 border-b border-border px-5 py-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-3 text-sm text-text-2">
            <span className="font-mono text-text-1">BTC/USDT</span>
            <span className="rounded-full border border-cyan/25 bg-cyan-dim px-2 py-1 text-[10px] uppercase tracking-[0.18em] text-cyan">
              Live signal
            </span>
          </div>
          <div className="flex flex-wrap items-center gap-3 font-mono text-sm">
            <span className="text-text-1">68,412.5</span>
            <span className="text-green">+1.42%</span>
            <span className="text-text-2">Confidence 94.2%</span>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {timeframes.map((frame) => (
            <button
              key={frame}
              className={cn(
                "rounded-full border px-3 py-2 text-xs uppercase tracking-[0.18em] transition",
                selectedFrame === frame
                  ? "border-cyan/30 bg-cyan-dim text-cyan"
                  : "border-border bg-bg-2 text-text-2 hover:border-border-strong hover:bg-bg-3 hover:text-text-1"
              )}
              onClick={() => setSelectedFrame(frame)}
              type="button"
            >
              {frame}
            </button>
          ))}
        </div>
      </div>

      <div className="grid gap-0 lg:grid-cols-[1fr_220px]">
        <div className="p-4 sm:p-5">
          <div className="rounded-[22px] border border-border bg-bg-0/70 p-3">
            <CandlestickChart candles={data.candles} markers={markers} height={340} showVolume={false} />
          </div>
        </div>

        <div className="border-t border-border p-4 lg:border-l lg:border-t-0">
          <div className="space-y-3">
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-[11px] uppercase tracking-[0.18em] text-text-3">Signal</div>
              <div className="mt-2 font-mono text-lg text-white">BUY</div>
              <div className="mt-1 text-sm text-text-2">Momentum expansion with controlled downside.</div>
            </div>
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-[11px] uppercase tracking-[0.18em] text-text-3">Take profit</div>
              <div className="mt-2 font-mono text-lg text-green">69,820.0</div>
            </div>
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-[11px] uppercase tracking-[0.18em] text-text-3">Stop loss</div>
              <div className="mt-2 font-mono text-lg text-red">67,620.0</div>
            </div>
            <div className="rounded-2xl border border-border bg-bg-2 p-4">
              <div className="text-[11px] uppercase tracking-[0.18em] text-text-3">Time to live</div>
              <div className="mt-2 font-mono text-lg text-white">3h 14m</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
