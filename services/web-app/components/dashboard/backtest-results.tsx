"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { BenchmarkComparisonChart } from "@/components/charts/BenchmarkComparisonChart";
import { PerformanceLineChart } from "@/components/charts/PerformanceLineChart";
import { Button } from "@/components/ui/button";
import { apiClient, type BacktestJobResponse } from "@/lib/api-client";
import { demoBenchmarkCurve, demoDrawdownCurve, demoEquityCurve, formatMoney, formatPercent } from "@/lib/dashboard/backtests";
import { cn } from "@/lib/utils";

type BacktestResultsProps = {
  backtestId: string;
};

function FailedState({ message }: { message: string }) {
  return (
    <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-red/10 text-red">!</div>
        <div className="flex-1">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">Backtest failed</div>
          <div className="mt-2 font-display text-xl font-semibold tracking-[-0.04em] text-white">Something went wrong</div>
          <p className="mt-1 text-sm leading-6 text-text-2">{message}</p>
        </div>
      </div>
      <div className="mt-8 flex flex-col gap-3 border-t border-border pt-6 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-text-2">Adjust your symbol, date range, or strategy and try again.</p>
        <Button asChild variant="outlineCyan" size="sm">
          <Link href="/dashboard/backtests">Run another backtest</Link>
        </Button>
      </div>
    </article>
  );
}

function LoadingState() {
  return (
    <div className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Loading</div>
      <div className="mt-4 h-8 w-56 animate-pulse rounded-full bg-bg-2" />
      <div className="mt-6 h-[280px] animate-pulse rounded-[20px] bg-bg-2" />
    </div>
  );
}

function MetricTile({
  label,
  value,
  tone = "text-white",
}: {
  label: string;
  value: string;
  tone?: string;
}) {
  return (
    <article className="rounded-[18px] border border-border bg-bg-2 p-4">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{label}</div>
      <div className={cn("mt-3 font-display text-2xl font-bold tracking-[-0.04em]", tone)}>{value}</div>
    </article>
  );
}

export function BacktestResults({ backtestId }: BacktestResultsProps) {
  const [job, setJob] = useState<BacktestJobResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const toastFiredRef = useRef(false);

  useEffect(() => {
    let mounted = true;

    async function loadBacktest() {
      try {
        const response = await apiClient.getBacktest(backtestId);
        if (mounted) {
          setJob(response);
        }
      } catch (requestError) {
        if (mounted) {
          setError(requestError instanceof Error ? requestError.message : "Unable to load backtest");
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    }

    loadBacktest();

    return () => {
      mounted = false;
    };
  }, [backtestId]);

  useEffect(() => {
    if (job?.status === "FAILED" && !toastFiredRef.current) {
      toastFiredRef.current = true;
      toast.error("Backtest failed", {
        description: job.errorMessage ?? "The backtest could not be completed.",
        duration: 8000,
      });
    }
  }, [job]);

  if (isLoading) {
    return <LoadingState />;
  }

  if (error || !job) {
    return <FailedState message={error ?? `No backtest job found for ID ${backtestId}. The job may have expired or the service was restarted.`} />;
  }

  if (job.status === "FAILED") {
    return <FailedState message={job.errorMessage ?? "The backtest could not be completed. Please check your configuration and try again."} />;
  }

  const result = job.result;

  if (!result) {
    return (
      <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtest queued</div>
        <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">The job is still running</h3>
        <p className="mt-3 text-sm leading-7 text-text-2">
          Refresh this page once it completes to see the metrics and trade history.
        </p>
      </article>
    );
  }

  const metricCards = [
    { label: "Total return", value: formatPercent(result.totalReturn * 100), tone: result.totalReturn >= 0 ? "text-green" : "text-red" },
    { label: "Sharpe ratio", value: result.sharpeRatio.toFixed(2), tone: "text-cyan" },
    { label: "Max drawdown", value: formatPercent(result.maxDrawdown * 100), tone: "text-red" },
    { label: "Win rate", value: formatPercent((result.winRate ?? 0) * 100), tone: "text-white" },
    { label: "Total trades", value: `${result.trades.length}`, tone: "text-white" },
    { label: "Profit factor", value: isFinite(result.profitFactor) ? result.profitFactor.toFixed(2) : "Unlimited", tone: "text-gold" },
  ];

  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-[1.3fr_0.7fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtest status</div>
          <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h3 className="font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
                Results for {backtestId}
              </h3>
              <p className="mt-2 text-sm leading-7 text-text-2">
                Loaded result metrics and trade summary for the completed run.
              </p>
            </div>
            <div className="rounded-3xl border border-border bg-bg-2 px-5 py-4 text-sm text-text-2">
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Current state</div>
              <div className="mt-2 font-display text-2xl font-semibold tracking-[-0.04em] text-white">{job.status}</div>
            </div>
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Actions</div>
          <div className="mt-4 flex flex-col gap-3">
            <Button asChild variant="outlineCyan">
              <Link href="/dashboard/backtests">Run another backtest</Link>
            </Button>
            <p className="text-sm leading-7 text-text-2">
              Use the configuration form to submit another symbol/date range combination and refresh this page with a
              new job id.
            </p>
          </div>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {metricCards.map((card) => (
          <MetricTile key={card.label} label={card.label} value={card.value} tone={card.tone} />
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Equity curve</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
            Portfolio growth over time
          </h3>
          <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
            <PerformanceLineChart color="#00d68f" height={300} points={demoEquityCurve} />
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Drawdown</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Peak-to-trough pressure</h3>
          <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
            <PerformanceLineChart color="#ff4d6a" height={300} points={demoDrawdownCurve} />
          </div>
        </article>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Benchmark comparison</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Strategy vs SPY</h3>
          </div>
          <div className="flex items-center gap-4 text-xs uppercase tracking-[0.22em] text-text-3">
            <span className="inline-flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-gold" />
              Strategy
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-blue-400" />
              SPY
            </span>
          </div>
        </div>
        <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-3">
          <BenchmarkComparisonChart benchmarkPoints={demoBenchmarkCurve} height={320} strategyPoints={demoEquityCurve} />
        </div>
      </section>

      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Trades</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
              Closed positions and P&L
            </h3>
          </div>
          <p className="text-sm text-text-2">Showing {result.trades.length} completed trades</p>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="min-w-[960px] w-full border-separate border-spacing-0">
            <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
              <tr>
                <th className="px-4 py-3 text-left">Symbol</th>
                <th className="px-4 py-3 text-left">P&L</th>
                <th className="px-4 py-3 text-left">Closed</th>
              </tr>
            </thead>
            <tbody>
              {result.trades.map((trade, index) => (
                <tr key={`${trade.symbol}-${index}`} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
                  <td className="border-t border-border px-4 py-4 font-semibold text-white">{trade.symbol}</td>
                  <td className={`border-t border-border px-4 py-4 font-mono ${trade.pnl >= 0 ? "text-green" : "text-red"}`}>
                    {formatMoney(trade.pnl)}
                  </td>
                  <td className="border-t border-border px-4 py-4 text-text-2">{job.updatedAt ?? job.createdAt ?? "2026-04-17"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
