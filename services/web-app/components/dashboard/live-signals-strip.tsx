"use client";

import { cn } from "@/lib/utils";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

type LiveSignalsStripProps = {
  signals: FilteredSignal[];
  selectedSymbol: string;
  onSymbolChange: (symbol: string) => void;
};

function signalChipClass(type: string, selected: boolean): string {
  const base = "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-[0.16em] cursor-pointer transition-all";
  if (type === "BUY") return cn(base, selected ? "ring-1 ring-buy-ring bg-buy-gradient text-white shadow-buy-glow" : "border-buy/40 text-buy hover:bg-buy/10");
  if (type === "SELL") return cn(base, selected ? "ring-1 ring-sell-ring bg-sell-gradient text-white shadow-sell-glow" : "border-sell/40 text-sell hover:bg-sell/10");
  return cn(base, selected ? "ring-1 ring-hold-ring bg-hold-gradient text-white shadow-hold-glow" : "border-hold/40 text-hold hover:bg-hold/10");
}

export function LiveSignalsStrip({ signals, selectedSymbol, onSymbolChange }: LiveSignalsStripProps) {
  const live = signals.filter((s) => s.live && s.type !== "HOLD");

  if (live.length === 0) return null;

  return (
    <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
      {live.map((signal) => (
        <button
          key={signal.id}
          type="button"
          onClick={() => onSymbolChange(signal.symbol)}
          className={signalChipClass(signal.type, signal.symbol === selectedSymbol)}
        >
          <span>{signal.symbol}</span>
          <span className="opacity-70">{signal.type}</span>
        </button>
      ))}
    </div>
  );
}
