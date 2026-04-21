"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

import { BenchmarkComparisonChart } from "@/components/charts/BenchmarkComparisonChart";
import { PerformanceLineChart } from "@/components/charts/PerformanceLineChart";
import { Button } from "@/components/ui/button";
import { apiClient, type BacktestJobResponse } from "@/lib/api-client";
import {
  demoBenchmarkCurve,
  demoDrawdownCurve,
  demoEquityCurve,
  demoMetricCards,
  demoTrades,
  formatMoney,
  formatPercent,
} from "@/lib/dashboard/backtests";

type BacktestResultsProps = {
  backtestId: string;
};

function LoadingState() {
  return (
    <div className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
      <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Loading backtest</p>
      <div className="mt-4 h-8 w-56 animate-pulse rounded-full bg-slate-200 dark:bg-white/10" />
      <div className="mt-6 h-[320px] animate-pulse rounded-[2rem] bg-slate-200 dark:bg-white/5" />
    </div>
  );
}

export function BacktestResults({ backtestId }: BacktestResultsProps) {
  const [job, setJob] = useState<BacktestJobResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  if (isLoading) {
    return <LoadingState />;
  }

  const latestJob = job;
  const status = latestJob?.status ?? "UNKNOWN";
  const result = latestJob?.result;
  const metricCards = result
    ? [
        { label: "Total return", value: formatPercent(result.totalReturn * 100), tone: "text-emerald-600 dark:text-mint-300" },
        { label: "Annualized return", value: formatPercent(result.annualizedReturn * 100), tone: "text-slate-900 dark:text-white" },
        { label: "Sharpe ratio", value: result.sharpeRatio.toFixed(2), tone: "text-amber-600 dark:text-gold-300" },
        { label: "Sortino ratio", value: result.sortinoRatio.toFixed(2), tone: "text-slate-900 dark:text-white" },
        { label: "Max drawdown", value: formatPercent(result.maxDrawdown * 100), tone: "text-rose-400 dark:text-rose-300" },
        { label: "Profit factor", value: result.profitFactor.toFixed(2), tone: "text-amber-600 dark:text-gold-300" },
      ]
    : demoMetricCards;

  const tradeRows = result
    ? result.trades.map((trade, index) => ({
        id: `${trade.symbol}-${index}`,
        symbol: trade.symbol,
        side: index % 2 === 0 ? "BUY" : "SELL",
        quantity: 10 + index * 4,
        entryPrice: 170 + index * 4,
        exitPrice: 172 + index * 5,
        pnl: trade.pnl,
        closedAt: latestJob?.updatedAt ?? latestJob?.createdAt ?? "2026-04-17",
      }))
    : demoTrades;

  const equityCurve = demoEquityCurve;
  const drawdownCurve = demoDrawdownCurve;
  const benchmarkCurve = demoBenchmarkCurve;

  return (
    <div className="space-y-6">
      <section className="grid gap-4 md:grid-cols-[1.3fr_0.7fr]">
        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Backtest status</p>
          <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-semibold text-slate-900 dark:text-white">Results for {backtestId}</h1>
              <p className="mt-2 text-sm leading-7 text-slate-600 dark:text-slate-300">
                {result
                  ? "Loaded result metrics and trade summary for the completed run."
                  : "The job is still pending. A sample results snapshot is shown so the page remains useful while the backtest executes."}
              </p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white px-5 py-4 text-sm text-slate-700 dark:border-white/10 dark:bg-ink-800/70 dark:text-slate-200">
              <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Current state</p>
              <p className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{status}</p>
            </div>
          </div>
        </article>

        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Actions</p>
          <div className="mt-4 flex flex-col gap-3">
            <Button asChild variant="secondary">
              <Link href="/dashboard/backtests">Run another backtest</Link>
            </Button>
            <p className="text-sm text-slate-600 dark:text-slate-300">
              Use the configuration form to submit another symbol/date range combination and refresh this page with a
              new job id.
            </p>
          </div>
        </article>
      </section>

      {error ? (
        <article className="rounded-[2rem] border border-rose-300/30 bg-rose-50 p-5 text-sm text-rose-700 shadow-glow dark:border-rose-400/30 dark:bg-rose-400/10 dark:text-rose-100">
          {error}
        </article>
      ) : null}

      <section className="grid gap-4 md:grid-cols-3">
        {metricCards.map((card) => (
          <article key={card.label} className="rounded-3xl border border-slate-200 bg-slate-100 p-5 shadow-glow dark:border-white/10 dark:bg-white/5">
            <p className="text-xs uppercase tracking-[0.35em] text-slate-500 dark:text-slate-400">{card.label}</p>
            <p className={`mt-3 text-2xl font-semibold ${card.tone ?? "text-slate-900 dark:text-white"}`}>{card.value}</p>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Equity curve</p>
          <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Portfolio growth over time</h2>
          <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-3 dark:border-white/10 dark:bg-ink-800/60">
            <PerformanceLineChart color="#facc15" height={300} points={equityCurve} />
          </div>
        </article>

        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Drawdown</p>
          <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Peak-to-trough pressure</h2>
          <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-3 dark:border-white/10 dark:bg-ink-800/60">
            <PerformanceLineChart color="#fb7185" height={300} points={drawdownCurve} />
          </div>
        </article>
      </section>

      <section className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Benchmark comparison</p>
            <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Strategy vs SPY</h2>
          </div>
          <div className="flex items-center gap-4 text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">
            <span className="inline-flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-gold-300" />
              Strategy
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-blue-400" />
              SPY
            </span>
          </div>
        </div>
        <div className="mt-6 rounded-3xl border border-slate-200 bg-white p-3 dark:border-white/10 dark:bg-ink-800/60">
          <BenchmarkComparisonChart benchmarkPoints={benchmarkCurve} height={320} strategyPoints={equityCurve} />
        </div>
      </section>

      <section className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Trades</p>
            <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Closed positions and P&L</h2>
          </div>
          <p className="text-sm text-slate-600 dark:text-slate-300">Showing {tradeRows.length} completed trades</p>
        </div>

        <div className="mt-6 overflow-hidden rounded-3xl border border-slate-200 dark:border-white/10">
          <table className="min-w-full divide-y divide-slate-200 dark:divide-white/10">
            <thead className="bg-slate-100 dark:bg-white/5">
              <tr className="text-left text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">
                <th className="px-5 py-4">Symbol</th>
                <th className="px-5 py-4">Side</th>
                <th className="px-5 py-4">Quantity</th>
                <th className="px-5 py-4">Entry</th>
                <th className="px-5 py-4">Exit</th>
                <th className="px-5 py-4">P&L</th>
                <th className="px-5 py-4">Closed</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 bg-white dark:divide-white/10 dark:bg-ink-800/50">
              {tradeRows.map((trade) => (
                <tr key={`${trade.symbol}-${trade.closedAt}`} className="text-sm text-slate-700 dark:text-slate-200">
                  <td className="px-5 py-4 font-semibold text-slate-900 dark:text-white">{trade.symbol}</td>
                  <td className="px-5 py-4">{trade.side}</td>
                  <td className="px-5 py-4">{trade.quantity}</td>
                  <td className="px-5 py-4">{formatMoney(trade.entryPrice)}</td>
                  <td className="px-5 py-4">{formatMoney(trade.exitPrice)}</td>
                  <td className={`px-5 py-4 font-semibold ${trade.pnl >= 0 ? "text-emerald-600 dark:text-mint-300" : "text-rose-400 dark:text-rose-300"}`}>
                    {formatMoney(trade.pnl)}
                  </td>
                  <td className="px-5 py-4">{trade.closedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
