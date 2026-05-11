"use client";

import { cn } from "@/lib/utils";
import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

type LiveSignalsStripProps = {
  readonly signals: FilteredSignal[];
  readonly selectedSignalId: string;
  readonly onSignalChange: (id: string) => void;
};

function signalChipClass(type: string, selected: boolean): string {
  const base = "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[11px] font-medium uppercase tracking-[0.16em] cursor-pointer transition-all";
  if (type === "BUY") return cn(base, selected ? "ring-1 ring-buy-ring bg-buy/10 text-emerald-200 border-buy/40 shadow-buy-glow" : "border-buy/40 text-buy hover:bg-buy/10");
  if (type === "SELL") return cn(base, selected ? "ring-1 ring-sell-ring bg-sell/10 text-rose-200 border-sell/40 shadow-sell-glow" : "border-sell/40 text-sell hover:bg-sell/10");
  return cn(base, selected ? "ring-1 ring-hold-ring bg-hold/10 text-amber-200 border-hold/40 shadow-hold-glow" : "border-hold/40 text-hold hover:bg-hold/10");
}

export function LiveSignalsStrip({ signals, selectedSignalId, onSignalChange }: LiveSignalsStripProps) {
  const live = signals.filter((s) => s.live && s.type !== "HOLD");

  if (live.length === 0) return null;

  return (
    <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-none">
      {live.map((signal) => (
        <button
          key={signal.id}
          type="button"
          onClick={() => onSignalChange(signal.id)}
          className={signalChipClass(signal.type, signal.id === selectedSignalId)}
        >
          <span>{signal.symbol}</span>
          <span className="opacity-70">{signal.type}</span>
        </button>
      ))}
    </div>
  );
}
