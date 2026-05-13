"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";
import { formatConfidence } from "@/lib/signal-utils";
import { cn } from "@/lib/utils";

type LiveSignalsStripProps = {
  readonly signals: readonly FilteredSignal[];
  readonly selectedSignalId: string;
  readonly onSignalChange: (id: string) => void;
};

type SignalSide = "BUY" | "SELL" | "HOLD";

const SIDE_ORDER: readonly SignalSide[] = ["BUY", "SELL", "HOLD"];

function sideClasses(type: string): { pill: string; chip: string; dot: string } {
  if (type === "BUY") {
    return {
      pill: "border-buy/40 bg-buy/10 text-emerald-200 hover:bg-buy/20 hover:border-buy/60",
      chip: "text-buy",
      dot: "bg-buy",
    };
  }
  if (type === "SELL") {
    return {
      pill: "border-sell/40 bg-sell/10 text-rose-200 hover:bg-sell/20 hover:border-sell/60",
      chip: "text-sell",
      dot: "bg-sell",
    };
  }
  return {
    pill: "border-hold/40 bg-hold/10 text-amber-200 hover:bg-hold/20 hover:border-hold/60",
    chip: "text-hold",
    dot: "bg-hold",
  };
}

export function LiveSignalsStrip({ signals, selectedSignalId, onSignalChange }: LiveSignalsStripProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [highlight, setHighlight] = useState(0);

  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const active = useMemo(
    () => signals.find((s) => s.id === selectedSignalId) ?? signals[0] ?? null,
    [signals, selectedSignalId]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return signals;
    return signals.filter((s) =>
      s.symbol.toLowerCase().includes(q) ||
      s.type.toLowerCase().includes(q) ||
      (s.reasoning ?? "").toLowerCase().includes(q)
    );
  }, [signals, query]);

  const grouped = useMemo(() => {
    const out = { BUY: [] as FilteredSignal[], SELL: [] as FilteredSignal[], HOLD: [] as FilteredSignal[] };
    for (const s of filtered) {
      const t = (s.type as SignalSide) in out ? (s.type as SignalSide) : "HOLD";
      out[t].push(s);
    }
    for (const t of SIDE_ORDER) {
      out[t].sort((a, b) => (b.confidence ?? 0) - (a.confidence ?? 0));
    }
    return out;
  }, [filtered]);

  const flat = useMemo(
    () => SIDE_ORDER.flatMap((side) => grouped[side]),
    [grouped]
  );

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
      if (e.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  useEffect(() => {
    if (open) {
      setHighlight(0);
      requestAnimationFrame(() => inputRef.current?.focus());
    } else {
      setQuery("");
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const pick = useCallback(
    (id: string) => {
      onSignalChange(id);
      setOpen(false);
    },
    [onSignalChange]
  );

  const onInputKey = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setHighlight((h) => Math.min(flat.length - 1, h + 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(0, h - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const target = flat[highlight];
      if (target) pick(target.id);
    }
  };

  if (signals.length === 0) return null;

  const activeStyle = active ? sideClasses(active.type) : sideClasses("HOLD");

  return (
    <div ref={rootRef} className="relative">
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          className={cn(
            "group inline-flex items-center gap-3 rounded-full border bg-bg-2 px-3 py-2 transition",
            "hover:border-border-strong",
            open && "border-cyan/40 ring-2 ring-cyan/20"
          )}
          aria-haspopup="listbox"
          aria-expanded={open}
        >
          {active && (
            <>
              <span
                className={cn(
                  "inline-flex items-center gap-2 rounded-full border px-2.5 py-1 font-mono text-[11px] tracking-[0.06em]",
                  activeStyle.pill
                )}
              >
                <span className="font-semibold text-white">{active.symbol}</span>
                <span>{active.type}</span>
              </span>
              <span className="font-mono text-[11px] text-text-2">
                conf {formatConfidence(active.confidence)} · {active.timeframe} · {active.age}
              </span>
            </>
          )}
          <svg
            className={cn("h-3.5 w-3.5 text-text-2 transition-transform", open && "rotate-180")}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </button>

        <span className="inline-flex items-center gap-2 rounded-full border border-cyan/20 bg-cyan-dim px-3 py-1.5 font-mono text-[11px] tracking-[0.18em] uppercase text-cyan">
          <span className="h-1.5 w-1.5 rounded-full bg-cyan animate-pulse-soft" />
          {signals.length} live
        </span>
        <kbd className="hidden md:inline-flex items-center gap-1 rounded-md border border-border bg-bg-2 px-2 py-1 font-mono text-[10px] tracking-[0.1em] uppercase text-text-3">
          ⌘K
        </kbd>
      </div>

      {open && (
        <div
          className="absolute left-0 top-[calc(100%+8px)] z-30 w-full max-w-md rounded-2xl border border-border-strong bg-bg-1/95 shadow-glow backdrop-blur-xl"
          role="listbox"
        >
          <div className="flex items-center gap-3 border-b border-border px-4 py-3">
            <svg
              className="h-4 w-4 text-text-2"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.3-4.3" />
            </svg>
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setHighlight(0);
              }}
              onKeyDown={onInputKey}
              placeholder={`Search ${signals.length} signals…`}
              className="flex-1 bg-transparent text-sm text-text-1 placeholder:text-text-3 focus:outline-none"
            />
            <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-text-3">
              {filtered.length} match{filtered.length === 1 ? "" : "es"}
            </span>
          </div>

          <div className="max-h-80 overflow-y-auto p-2">
            {filtered.length === 0 ? (
              <div className="px-3 py-8 text-center text-sm text-text-2">No signals match &ldquo;{query}&rdquo;.</div>
            ) : (
              SIDE_ORDER.map((side) => {
                const items = grouped[side];
                if (items.length === 0) return null;
                const ss = sideClasses(side);
                return (
                  <div key={side} className="mb-1 last:mb-0">
                    <div className="flex items-center gap-2 px-3 py-2 font-mono text-[10px] uppercase tracking-[0.22em] text-text-3">
                      <span className={cn("h-1.5 w-1.5 rounded-full", ss.dot)} />
                      <span>{side}</span>
                      <span className={ss.chip}>· {items.length}</span>
                    </div>
                    {items.map((s) => {
                      const flatIndex = flat.findIndex((f) => f.id === s.id);
                      const isHighlight = flatIndex === highlight;
                      const isSelected = s.id === active?.id;
                      return (
                        <button
                          key={s.id}
                          type="button"
                          onMouseEnter={() => setHighlight(flatIndex)}
                          onClick={() => pick(s.id)}
                          className={cn(
                            "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition",
                            isHighlight && "bg-white/[0.04]",
                            isSelected && "bg-cyan-dim"
                          )}
                          role="option"
                          aria-selected={isSelected}
                        >
                          <span className="w-14 font-mono text-[13px] font-semibold text-white">{s.symbol}</span>
                          <span className={cn("font-mono text-[10px] font-semibold uppercase tracking-[0.12em]", ss.chip)}>
                            {s.type}
                          </span>
                          <span className="ml-auto flex items-center gap-3 font-mono text-[11px] text-text-2">
                            <span>{s.timeframe}</span>
                            <span className="text-text-3">·</span>
                            <span className="text-cyan">{formatConfidence(s.confidence)}</span>
                            <span className="text-text-3">·</span>
                            <span>{s.age}</span>
                          </span>
                        </button>
                      );
                    })}
                  </div>
                );
              })
            )}
          </div>

          <div className="flex items-center justify-between border-t border-border px-4 py-2 font-mono text-[10px] uppercase tracking-[0.18em] text-text-3">
            <span className="flex items-center gap-3">
              <span className="flex items-center gap-1">
                <kbd className="rounded border border-border bg-bg-2 px-1.5 py-0.5">↑↓</kbd>
                navigate
              </span>
              <span className="flex items-center gap-1">
                <kbd className="rounded border border-border bg-bg-2 px-1.5 py-0.5">↵</kbd>
                select
              </span>
            </span>
            <span className="flex items-center gap-1">
              <kbd className="rounded border border-border bg-bg-2 px-1.5 py-0.5">esc</kbd>
              close
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
