"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { PerformanceLineChart } from "@/components/charts/PerformanceLineChart";
import { Button } from "@/components/ui/button";
import { apiClient, type BacktestJobResponse } from "@/lib/api-client";
import { demoEquityCurve, formatMoney, formatPercent } from "@/lib/dashboard/backtests";
import { cn } from "@/lib/utils";

const symbolOptions = [
  { symbol: "BTC/USDT", label: "Bitcoin", price: 68412.5 },
  { symbol: "ETH/USDT", label: "Ethereum", price: 3328.4 },
  { symbol: "AAPL", label: "Apple", price: 178.5 },
  { symbol: "NVDA", label: "NVIDIA", price: 846.2 },
  { symbol: "TSLA", label: "Tesla", price: 187.8 },
] as const;

const strategyOptions = [
  { id: "momentum-1", name: "Momentum Sweep", allocation: 0.35, risk: "Tighter stops, faster rotation" },
  { id: "trend-2", name: "Trend Rider", allocation: 0.55, risk: "Balanced exposure with medium risk" },
  { id: "swing-3", name: "Swing Core", allocation: 0.75, risk: "Higher conviction, longer holds" },
] as const;

const backtestSchema = z
  .object({
    symbol: z.enum(symbolOptions.map((option) => option.symbol) as [string, ...string[]]),
    from: z.string().min(1, "Start date is required"),
    to: z.string().min(1, "End date is required"),
    strategyId: z.enum(strategyOptions.map((option) => option.id) as [string, ...string[]]),
    initialCapital: z.coerce.number().positive("Initial capital must be greater than zero"),
    stopLossPct: z.coerce.number().min(0.1).max(20),
    takeProfitPct: z.coerce.number().min(0.1).max(50),
  })
  .refine(
    (value) => {
      const from = new Date(`${value.from}T00:00:00Z`);
      const to = new Date(`${value.to}T00:00:00Z`);
      return !Number.isNaN(from.getTime()) && !Number.isNaN(to.getTime()) && to >= from;
    },
    {
      message: "End date must be on or after the start date",
      path: ["to"],
    }
  );

type BacktestFormValues = z.infer<typeof backtestSchema>;

const fieldClassName =
  "w-full rounded-2xl border border-border bg-bg-2 px-4 py-3 text-sm text-white outline-none transition placeholder:text-text-3 focus:border-cyan/40";

const optionClassName = "bg-bg-2 text-white";

function getSymbolPrice(symbol: string) {
  return symbolOptions.find((option) => option.symbol === symbol)?.price ?? symbolOptions[0].price;
}

function getStrategy(strategyId: string) {
  return strategyOptions.find((option) => option.id === strategyId) ?? strategyOptions[0];
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

export function BacktestForm() {
  const [submission, setSubmission] = useState<BacktestJobResponse | null>(null);
  const [previewJob, setPreviewJob] = useState<BacktestJobResponse | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    watch,
    handleSubmit,
    formState: { errors },
  } = useForm<BacktestFormValues>({
    resolver: zodResolver(backtestSchema),
    defaultValues: {
      symbol: symbolOptions[0].symbol,
      from: "2026-04-01",
      to: "2026-04-16",
      strategyId: strategyOptions[1].id,
      initialCapital: 10000,
      stopLossPct: 4.5,
      takeProfitPct: 12,
    },
  });

  const symbol = watch("symbol");
  const strategyId = watch("strategyId");
  const initialCapital = watch("initialCapital");
  const stopLossPct = watch("stopLossPct");
  const takeProfitPct = watch("takeProfitPct");

  const selectedSymbol = symbolOptions.find((option) => option.symbol === symbol) ?? symbolOptions[0];
  const selectedStrategy = getStrategy(strategyId);
  const estimatedQuantity = Math.max(1, Math.floor((Number(initialCapital || 0) * selectedStrategy.allocation) / selectedSymbol.price));
  const estimatedNotional = estimatedQuantity * selectedSymbol.price;
  const symbolField = register("symbol");

  const previewMetrics = useMemo(() => {
    const job = previewJob?.result;

    return job
      ? [
          { label: "Total Return", value: formatPercent(job.totalReturn * 100), tone: job.totalReturn >= 0 ? "text-green" : "text-red" },
          { label: "Sharpe", value: job.sharpeRatio.toFixed(2), tone: "text-cyan" },
          { label: "Max DD", value: formatPercent(job.maxDrawdown * 100), tone: "text-red" },
          { label: "Win Rate", value: formatPercent((job.winRate ?? 0) * 100), tone: "text-white" },
          { label: "Total Trades", value: `${job.trades.length}`, tone: "text-white" },
          { label: "Profit Factor", value: isFinite(job.profitFactor) ? job.profitFactor.toFixed(2) : "Unlimited", tone: "text-gold" },
        ]
      : [];
  }, [previewJob]);

  const onSubmit = handleSubmit(async (values) => {
    setIsSubmitting(true);
    setServerError(null);
    setSubmission(null);
    setPreviewJob(null);

    try {
      const response = await apiClient.submitBacktest({
        symbol: values.symbol,
        from: values.from,
        to: values.to,
        quantity: Math.max(1, Math.floor((values.initialCapital * selectedStrategy.allocation) / getSymbolPrice(values.symbol))),
      });

      window.setTimeout(async () => {
        try {
          const refreshed = await apiClient.getBacktest(response.id);
          setSubmission(refreshed);
          setPreviewJob(refreshed);
        } catch {
          setSubmission(response);
        } finally {
          setIsSubmitting(false);
        }
      }, 1800);
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "Unable to submit backtest");
      setIsSubmitting(false);
    }
  });

  const result = previewJob?.result;

  return (
    <div className="grid gap-6 lg:grid-cols-[1.08fr_0.92fr]">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="flex flex-col gap-3">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtest config</div>
          <h2 className="font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
            Configure and submit a run
          </h2>
          <p className="max-w-2xl text-sm leading-7 text-text-2">
            Pick a strategy, pair, date range, and capital base. The backend accepts the job immediately, then the
            client refreshes the results after a short run animation.
          </p>
        </div>

        <form className="mt-8 space-y-5" onSubmit={onSubmit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Pair</span>
              <select
                className={fieldClassName}
                {...symbolField}
                onChange={async (event) => {
                  symbolField.onChange(event);
                  try {
                    const available = await apiClient.checkSymbolAvailability(event.target.value);
                    if (!available) {
                      toast.warning("No market data available", {
                        description: `${event.target.value} does not have enough history for this backtest.`,
                        duration: 5000,
                      });
                    }
                  } catch {
                    // ignore symbol availability failures
                  }
                }}
              >
                {symbolOptions.map((option) => (
                  <option key={option.symbol} className={optionClassName} value={option.symbol}>
                    {option.symbol} - {option.label}
                  </option>
                ))}
              </select>
              {errors.symbol ? <p className="mt-2 text-sm text-red">{errors.symbol.message}</p> : null}
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Strategy</span>
              <select className={fieldClassName} {...register("strategyId")}>
                {strategyOptions.map((strategy) => (
                  <option key={strategy.id} className={optionClassName} value={strategy.id}>
                    {strategy.name}
                  </option>
                ))}
              </select>
              {errors.strategyId ? <p className="mt-2 text-sm text-red">{errors.strategyId.message}</p> : null}
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">From</span>
              <input type="date" className={fieldClassName} {...register("from")} />
              {errors.from ? <p className="mt-2 text-sm text-red">{errors.from.message}</p> : null}
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">To</span>
              <input type="date" className={fieldClassName} {...register("to")} />
              {errors.to ? <p className="mt-2 text-sm text-red">{errors.to.message}</p> : null}
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Initial capital</span>
              <input type="number" min="1" step="100" className={fieldClassName} {...register("initialCapital")} />
              {errors.initialCapital ? <p className="mt-2 text-sm text-red">{errors.initialCapital.message}</p> : null}
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Stop loss %</span>
              <input type="number" min="0.1" step="0.1" className={fieldClassName} {...register("stopLossPct")} />
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.22em] text-text-3">Take profit %</span>
              <input type="number" min="0.1" step="0.1" className={fieldClassName} {...register("takeProfitPct")} />
            </label>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <article className="rounded-[20px] border border-border bg-bg-2 p-5">
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Derived size</div>
              <div className="mt-3 font-display text-3xl font-bold tracking-[-0.05em] text-white">{estimatedQuantity} shares</div>
              <div className="mt-2 text-sm text-text-2">
                Estimated notional {formatMoney(estimatedNotional)} using the selected strategy allocation.
              </div>
            </article>

            <article className="rounded-[20px] border border-cyan/25 bg-cyan-dim p-5">
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Risk envelope</div>
              <div className="mt-3 text-lg font-semibold text-white">{selectedStrategy.name}</div>
              <div className="mt-2 text-sm leading-7 text-text-1">{selectedStrategy.risk}</div>
              <div className="mt-3 font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">
                SL {Number(stopLossPct).toFixed(1)}% · TP {Number(takeProfitPct).toFixed(1)}%
              </div>
            </article>
          </div>

          {serverError ? <p className="text-sm text-red">{serverError}</p> : null}

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <Button className="sm:min-w-52" disabled={isSubmitting} size="xl" type="submit" variant="cyan">
              {isSubmitting ? "Running backtest..." : "Run backtest"}
            </Button>
            <p className="text-sm text-text-2">
              The job is accepted immediately and the result panel updates after a short run animation.
            </p>
          </div>
        </form>
      </section>

      <aside className="space-y-6">
        {!result ? (
          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Results</div>
            <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">No results yet</h3>
            <p className="mt-3 text-sm leading-7 text-text-2">
              Submit a run to see the six summary tiles and equity curve appear here.
            </p>
          </article>
        ) : (
          <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
            <div className="flex items-center justify-between gap-4">
              <div>
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtest result</div>
                <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
                  {submission?.id ?? "Completed job"}
                </h3>
              </div>
              <span className="rounded-full border border-cyan/25 bg-cyan-dim px-3 py-1 text-[10px] uppercase tracking-[0.22em] text-cyan">
                {submission?.status ?? "COMPLETED"}
              </span>
            </div>

            <div className="mt-6 grid gap-4 md:grid-cols-2">
              {previewMetrics.map((metric) => (
                <MetricTile key={metric.label} label={metric.label} value={metric.value} tone={metric.tone} />
              ))}
            </div>

            <div className="mt-6 rounded-[22px] border border-border bg-bg-0/70 p-4">
              <PerformanceLineChart color="#00d68f" height={280} points={demoEquityCurve} />
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <Button asChild size="sm" variant="outlineCyan">
                <Link href={`/dashboard/backtests/${submission?.id ?? ""}`}>Open full report</Link>
              </Button>
            </div>
          </article>
        )}
      </aside>
    </div>
  );
}
