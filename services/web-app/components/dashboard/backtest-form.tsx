"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { apiClient, type BacktestJobResponse } from "@/lib/api-client";
import { cn } from "@/lib/utils";

const symbolOptions = [
  { symbol: "AAPL", label: "Apple", price: 178.5 },
  { symbol: "MSFT", label: "Microsoft", price: 421.1 },
  { symbol: "NVDA", label: "NVIDIA", price: 846.2 },
  { symbol: "TSLA", label: "Tesla", price: 187.8 },
  { symbol: "AMD", label: "AMD", price: 171.25 },
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
  "w-full rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white outline-none ring-0 transition placeholder:text-slate-500 focus:border-gold-300/60";

function getSymbolPrice(symbol: string) {
  return symbolOptions.find((option) => option.symbol === symbol)?.price ?? symbolOptions[0].price;
}

function getStrategy(strategyId: string) {
  return strategyOptions.find((option) => option.id === strategyId) ?? strategyOptions[0];
}

export function BacktestForm() {
  const [submission, setSubmission] = useState<BacktestJobResponse | null>(null);
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
    },
  });

  const symbol = watch("symbol");
  const strategyId = watch("strategyId");
  const initialCapital = watch("initialCapital");

  const selectedSymbol = symbolOptions.find((option) => option.symbol === symbol) ?? symbolOptions[0];
  const selectedStrategy = getStrategy(strategyId);
  const estimatedQuantity = Math.max(1, Math.floor((Number(initialCapital || 0) * selectedStrategy.allocation) / selectedSymbol.price));
  const estimatedNotional = estimatedQuantity * selectedSymbol.price;

  const onSubmit = handleSubmit(async (values) => {
    setIsSubmitting(true);
    setServerError(null);
    setSubmission(null);

    try {
      const response = await apiClient.submitBacktest({
        symbol: values.symbol,
        from: values.from,
        to: values.to,
        quantity: Math.max(1, Math.floor((values.initialCapital * selectedStrategy.allocation) / getSymbolPrice(values.symbol))),
      });

      setSubmission(response);
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "Unable to submit backtest");
    } finally {
      setIsSubmitting(false);
    }
  });

  return (
    <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
        <div className="flex flex-col gap-3">
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Backtest setup</p>
          <h1 className="text-3xl font-semibold text-white">Configure and submit a backtest run</h1>
          <p className="max-w-2xl text-sm leading-7 text-slate-300">
            Pick a symbol, date range, strategy profile, and starting capital. The form validates the inputs locally,
            then posts the run to the backtesting service with a derived order size.
          </p>
        </div>

        <form className="mt-8 space-y-5" onSubmit={onSubmit}>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Symbol</span>
              <select className={fieldClassName} {...register("symbol")}>
                {symbolOptions.map((option) => (
                  <option key={option.symbol} value={option.symbol}>
                    {option.symbol} - {option.label}
                  </option>
                ))}
              </select>
              {errors.symbol ? <p className="mt-2 text-sm text-rose-300">{errors.symbol.message}</p> : null}
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Strategy</span>
              <select className={fieldClassName} {...register("strategyId")}>
                {strategyOptions.map((strategy) => (
                  <option key={strategy.id} value={strategy.id}>
                    {strategy.name}
                  </option>
                ))}
              </select>
              {errors.strategyId ? <p className="mt-2 text-sm text-rose-300">{errors.strategyId.message}</p> : null}
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">From</span>
              <input type="date" className={fieldClassName} {...register("from")} />
              {errors.from ? <p className="mt-2 text-sm text-rose-300">{errors.from.message}</p> : null}
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">To</span>
              <input type="date" className={fieldClassName} {...register("to")} />
              {errors.to ? <p className="mt-2 text-sm text-rose-300">{errors.to.message}</p> : null}
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-[1fr_1fr]">
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Initial capital</span>
              <input type="number" min="1" step="100" className={fieldClassName} {...register("initialCapital")} />
              {errors.initialCapital ? (
                <p className="mt-2 text-sm text-rose-300">{errors.initialCapital.message}</p>
              ) : null}
            </label>

            <div className="rounded-3xl border border-white/10 bg-ink-800/60 p-4">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">Derived order size</p>
              <p className="mt-3 text-3xl font-semibold text-white">{estimatedQuantity} shares</p>
              <p className="mt-2 text-sm text-slate-300">
                Estimated notional {estimatedNotional.toLocaleString("en-US", { style: "currency", currency: "USD" })} at{" "}
                {selectedSymbol.symbol} reference price.
              </p>
            </div>
          </div>

          <div className="rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5 text-sm text-slate-200">
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Selected strategy</p>
            <p className="mt-3 text-lg font-semibold text-white">{selectedStrategy.name}</p>
            <p className="mt-2 leading-7">{selectedStrategy.risk}</p>
            <p className="mt-3 text-xs uppercase tracking-[0.3em] text-slate-400">
              Allocation {Math.round(selectedStrategy.allocation * 100)}%
            </p>
          </div>

          {serverError ? <p className="text-sm text-rose-300">{serverError}</p> : null}

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <Button className="sm:min-w-48" disabled={isSubmitting} type="submit">
              {isSubmitting ? "Submitting backtest..." : "Run backtest"}
            </Button>
            <p className="text-sm text-slate-400">
              The backend accepts the validated symbol, date range, and derived quantity payload.
            </p>
          </div>
        </form>
      </section>

      <aside className="space-y-6">
        <article className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Why this form matters</p>
          <div className="mt-4 space-y-4 text-sm leading-7 text-slate-300">
            <p>Each input is validated before the request leaves the browser.</p>
            <p>The selected strategy influences sizing without making the backend contract more complex.</p>
            <p>Submitted jobs return immediately so the later results and status views can attach to the same flow.</p>
          </div>
        </article>

        <article
          className={cn(
            "rounded-[2rem] border p-6 shadow-glow transition",
            submission ? "border-mint-300/30 bg-mint-400/10" : "border-white/10 bg-white/5"
          )}
        >
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Latest submission</p>
          {submission ? (
            <div className="mt-4 space-y-3 text-sm text-slate-200">
              <p className="text-2xl font-semibold text-white">{submission.status}</p>
              <p>
                Backtest ID: <span className="text-white">{submission.id}</span>
              </p>
              <p>
                Submitted for <span className="text-white">{submission.request.symbol}</span> from{" "}
                <span className="text-white">{submission.request.from}</span> to{" "}
                <span className="text-white">{submission.request.to}</span>.
              </p>
              <p>
                Quantity queued: <span className="text-white">{submission.request.quantity}</span>
              </p>
              <div className="pt-2">
                <Button asChild size="sm" variant="secondary">
                  <Link href={`/dashboard/backtests/${submission.id}`}>View results</Link>
                </Button>
              </div>
            </div>
          ) : (
            <p className="mt-4 text-sm leading-7 text-slate-300">
              Submit a run to see the accepted job payload here. The backtest result summary will appear once the
              execution service completes the async job.
            </p>
          )}
        </article>
      </aside>
    </div>
  );
}
