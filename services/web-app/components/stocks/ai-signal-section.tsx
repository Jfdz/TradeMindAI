"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient, type SignalResponse } from "@/lib/api-client";
import { resolveExpectedMovePct, formatPredictedChange } from "@/lib/signal-utils";

type Props = {
  readonly ticker: string;
};

function formatConfidence(v: number): string {
  return `${(v * 100).toFixed(1)}%`;
}

function SignalBadge({ type }: { readonly type: SignalResponse["type"] }) {
  const colors: Record<SignalResponse["type"], string> = {
    BUY: "text-green-400 border-green-400/30 bg-green-400/10",
    SELL: "text-red-400 border-red-400/30 bg-red-400/10",
    HOLD: "text-yellow-400 border-yellow-400/30 bg-yellow-400/10",
  };
  return (
    <span className={`rounded-full border px-3 py-1 text-[10px] font-semibold uppercase tracking-widest ${colors[type]}`}>
      {type}
    </span>
  );
}

export function AISignalSection({ ticker }: Props) {
  const { data, status } = useQuery({
    queryKey: ["signals-for-ticker", ticker],
    queryFn: async () => {
      const response = await apiClient.getSignals();
      return response.content.filter((s) => s.symbol === ticker);
    },
  });

  if (status === "pending") {
    return (
      <div className="space-y-3 rounded-xl border border-cyan-500/30 bg-card p-4 shadow-[0_0_20px_rgba(6,182,212,0.08)]">
        <div className="h-4 w-32 animate-pulse rounded-full bg-muted" />
        <div className="h-16 animate-pulse rounded-lg bg-muted" />
      </div>
    );
  }

  const signal = data?.[0] ?? null;

  if (!signal) {
    return (
      <div className="rounded-xl border border-cyan-500/30 bg-card p-4 shadow-[0_0_20px_rgba(6,182,212,0.08)]">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Today&apos;s live signal
        </h3>
        <p className="mt-3 text-sm text-muted-foreground">No active prediction for {ticker}.</p>
      </div>
    );
  }

  const live = Date.now() - new Date(signal.generatedAt).getTime() < 1000 * 60 * 60 * 24;
  const movePct = resolveExpectedMovePct(signal.entryPrice, signal.targetPrice, signal.predictedChangePct);
  const { label: moveLabel, colorClass: moveColorClass } = formatPredictedChange(movePct, signal.type);

  return (
    <div className="space-y-3 rounded-xl border border-cyan-500/30 bg-card p-4 shadow-[0_0_20px_rgba(6,182,212,0.08)]">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-cyan-400">
          Today&apos;s live signal
        </h3>
        <div className="flex items-center gap-2">
          <SignalBadge type={signal.type} />
          <span className={`text-[10px] uppercase tracking-wider ${live ? "text-green-400" : "text-muted-foreground"}`}>
            {live ? "Live" : "Pending"}
          </span>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div className="rounded-lg bg-muted/50 p-2">
          <p className="text-muted-foreground">Confidence</p>
          <p className="mt-0.5 font-mono font-semibold">{formatConfidence(signal.confidence)}</p>
        </div>
        <div className="rounded-lg bg-muted/50 p-2">
          <p className="text-muted-foreground">Timeframe</p>
          <p className="mt-0.5 font-mono font-semibold">{signal.timeframe}</p>
        </div>
        {movePct != null && (
          <div className="rounded-lg bg-muted/50 p-2">
            <p className="text-muted-foreground">Expected move</p>
            <p className={`mt-0.5 font-mono font-semibold ${moveColorClass}`}>{moveLabel}</p>
          </div>
        )}
      </div>
    </div>
  );
}
