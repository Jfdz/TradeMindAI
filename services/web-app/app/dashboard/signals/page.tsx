"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { PaginationControls } from "@/components/dashboard/pagination-controls";
import { StockLogo } from "@/components/ui/stock-logo";
import { fetchSignalsPageData } from "@/lib/dashboard/client-data";
import { useStockLogos } from "@/lib/dashboard/use-stock-logos";
import { formatConfidence } from "@/lib/signal-utils";
import { cn } from "@/lib/utils";

type FilterValue = "ALL" | "BUY" | "SELL" | "HOLD";

const filterOptions: FilterValue[] = ["ALL", "BUY", "SELL", "HOLD"];

function formatPrice(value: number | null) {
  if (value == null || Number.isNaN(value)) {
    return <span className="text-text-3" title="Awaiting market data">—</span>;
  }

  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

function pickSignalLabel(isLoading: boolean, total: number | undefined) {
  if (isLoading) return "Loading signals…";
  if (!total) return "Signal feed";
  return total === 1 ? "1 live signal" : `${total} live signals`;
}

function pickSignalBadgeClass(signalType: string) {
  if (signalType === "BUY") return "ring-1 ring-buy-ring bg-buy/10 text-emerald-200 border-buy/40 shadow-buy-glow";
  if (signalType === "SELL") return "ring-1 ring-sell-ring bg-sell/10 text-rose-200 border-sell/40 shadow-sell-glow";
  return "ring-1 ring-hold-ring bg-hold/10 text-amber-200 border-hold/40 shadow-hold-glow";
}

function pickStatusBadgeClass(status: "NEW" | "LIVE" | "ACTIVE") {
  if (status === "NEW")
    return "border-cyan-bright/50 bg-cyan-bright/[0.10] text-cyan-bright shadow-neon-soft animate-pulse-soft";
  if (status === "LIVE") return "border-green/40 bg-green/[0.10] text-green";
  return "border-border bg-bg-2 text-text-2";
}

function SignalsContent() {
  const searchParams = useSearchParams();
  const page = Math.max(0, Number.parseInt(searchParams.get("page") ?? "0", 10));

  const [activeFilter, setActiveFilter] = useState<FilterValue>("ALL");
  const {
    data,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["signals", page],
    queryFn: () => fetchSignalsPageData({ page }),
  });

  const signals = useMemo(() => data?.items ?? [], [data?.items]);
  const pageInfo = data?.pageInfo;

  const signalLogos = useStockLogos(useMemo(() => signals.map((s) => s.symbol), [signals]));

  const filteredSignals = useMemo(() => {
    if (activeFilter === "ALL") {
      return signals;
    }
    return signals.filter((signal) => signal.type === activeFilter);
  }, [activeFilter, signals]);

  return (
    <div className="space-y-8">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="inline-flex items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
              <span className="h-2 w-2 rounded-full bg-green animate-pulse-soft" />
              {" Live signals"}
            </div>
            <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
              {pickSignalLabel(isLoading, data?.pageInfo?.totalElements)}
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-text-2">
              Review the latest signal feed, narrow the list with filters, and open a full signal detail page for more
              context.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {filterOptions.map((filter) => (
              <Button
                key={filter}
                size="sm"
                variant={activeFilter === filter ? "cyan" : "outline"}
                onClick={() => setActiveFilter(filter)}
              >
                {filter}
              </Button>
            ))}
          </div>
        </div>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        {(() => {
          if (isLoading) {
            return (
              <div className="space-y-4">
                <div className="h-8 w-64 animate-pulse rounded-full bg-bg-2" />
                <div className="h-[360px] animate-pulse rounded-[20px] bg-bg-2" />
              </div>
            );
          }
          if (error) {
            return (
              <div className="rounded-2xl border border-red/30 bg-red/10 p-4 text-sm text-red">
                {error instanceof Error ? error.message : "Unable to load signals"}
              </div>
            );
          }
          return (
            <>
              <div className="overflow-x-auto">
                <table className="min-w-[1200px] w-full border-separate border-spacing-0">
                  <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                    <tr>
                      <th className="px-4 py-3 text-left">Pair</th>
                      <th className="px-4 py-3 text-left">Signal</th>
                      <th className="px-4 py-3 text-left">TF</th>
                      <th className="px-4 py-3 text-left">Entry</th>
                      <th className="px-4 py-3 text-left">Take Profit</th>
                      <th className="px-4 py-3 text-left">Stop Loss</th>
                      <th className="px-4 py-3 text-left">Confidence</th>
                      <th className="px-4 py-3 text-left">Status</th>
                      <th className="px-4 py-3 text-left">Reasoning</th>
                      <th className="px-4 py-3 text-left">Time</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSignals.map((signal, index) => (
                      <tr
                        key={signal.id}
                        className={cn(
                          "relative cursor-pointer transition hover:bg-white/[0.025]",
                          index % 2 === 0 ? "bg-white/[0.012]" : "",
                        )}
                      >
                        <td className="border-t border-border px-4 py-4">
                          <Link
                            href={`/dashboard/signals/${signal.id}`}
                            aria-label={`Open ${signal.symbol} signal detail`}
                            className="absolute inset-0 z-0"
                          />
                          <Link
                            href={`/dashboard/stocks/${signal.symbol}`}
                            className="group relative z-10 inline-flex items-center gap-2"
                          >
                            <StockLogo ticker={signal.symbol} logoUrl={signalLogos?.[signal.symbol]} size={24} />
                            <div>
                              <div className="font-display text-base font-semibold tracking-[-0.03em] text-white group-hover:text-cyan transition-colors">
                                {signal.symbol}
                              </div>
                              <div className="mt-1 text-xs uppercase tracking-[0.22em] text-text-3">{signal.age}</div>
                            </div>
                          </Link>
                        </td>
                        <td className="border-t border-border px-4 py-4">
                          <span className={cn("rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.22em]", pickSignalBadgeClass(signal.type))}>
                            {signal.type}
                          </span>
                        </td>
                        <td className="border-t border-border px-4 py-4 font-mono text-text-1">{signal.timeframe}</td>
                        <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatPrice(signal.entry)}</td>
                        <td className="border-t border-border px-4 py-4 font-mono text-green">{formatPrice(signal.takeProfit)}</td>
                        <td className="border-t border-border px-4 py-4 font-mono text-red">{formatPrice(signal.stopLoss)}</td>
                        <td className="border-t border-border px-4 py-4">
                          <div className="w-44">
                            <div className="text-xs text-text-2">
                              {formatConfidence(signal.confidence)}
                            </div>
                            <div className="mt-2 h-2 overflow-hidden rounded-full bg-bg-3">
                              <div
                                className="h-full rounded-full bg-gradient-to-r from-cyan via-cyan-bright to-green"
                                style={{ width: `${signal.confidence * 100}%`, boxShadow: "0 0 8px rgba(0,200,212,0.35)" }}
                              />
                            </div>
                          </div>
                        </td>
                        <td className="border-t border-border px-4 py-4">
                          <span className={cn("rounded-full border px-3 py-1 text-[10px] uppercase tracking-[0.22em]", pickStatusBadgeClass(signal.status))}>
                            {signal.status}
                          </span>
                        </td>
                        <td className="border-t border-border px-4 py-4 text-sm leading-6 text-text-2">
                          {!signal.reasoningStatus || signal.reasoningStatus === "PENDING" ? (
                            <div className="space-y-1.5">
                              <div className="h-3 w-40 animate-pulse rounded-full bg-bg-2" />
                              <div className="h-3 w-28 animate-pulse rounded-full bg-bg-2" />
                            </div>
                          ) : (
                            signal.reasoning
                          )}
                        </td>
                        <td className="border-t border-border px-4 py-4">
                          <div className="font-mono text-text-1">{signal.age}</div>
                          <div className="mt-1 text-xs text-text-3">{signal.generatedLabel}</div>
                        </td>
                      </tr>
                    ))}
                    {filteredSignals.length === 0 ? (
                      <tr>
                        <td className="border-t border-border px-4 py-10 text-center text-sm text-text-2" colSpan={10}>
                          No signals match the current filter.
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
              {pageInfo && (
                <PaginationControls
                  pageNumber={pageInfo.pageNumber}
                  totalPages={pageInfo.totalPages}
                  isFirst={pageInfo.isFirst}
                  isLast={pageInfo.isLast}
                />
              )}
            </>
          );
        })()}
      </section>
    </div>
  );
}

export default function SignalsPage() {
  return (
    <Suspense>
      <SignalsContent />
    </Suspense>
  );
}
