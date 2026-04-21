import Link from "next/link";
import { notFound } from "next/navigation";

import { SignalChart } from "@/components/dashboard/signal-chart";
import { formatConfidence, formatPrice, formatSignalDate, getSignalById, getSignalChartData } from "@/lib/dashboard/signals";

export default function SignalDetailPage({ params }: { params: { signalId: string } }) {
  const signal = getSignalById(params.signalId);

  if (!signal) {
    notFound();
  }

  const chartData = getSignalChartData(signal);

  return (
    <div className="space-y-8">
      <section className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
        <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Signal detail</p>
        <div className="mt-3 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h1 className="text-3xl font-semibold text-slate-900 dark:text-white">{signal.symbol}</h1>
            <p className="mt-2 text-sm uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">
              {signal.type} · {signal.timeframe}
            </p>
          </div>

          <Link
            className="inline-flex items-center justify-center rounded-full border border-slate-200 bg-slate-100 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-700 dark:border-white/10 dark:bg-white/5 dark:text-slate-200"
            href="/dashboard/signals"
          >
            Back to signals
          </Link>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Chart preview</p>
              <h2 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Price action and signal marker</h2>
            </div>
            <span className="rounded-full border border-slate-200 bg-slate-100 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300">
              Placeholder
            </span>
          </div>

          <div className="mt-6 flex min-h-[340px] items-center justify-center rounded-3xl border border-dashed border-slate-300 bg-slate-200/60 p-8 text-center dark:border-white/15 dark:bg-ink-800/60">
            <SignalChart candles={chartData.candles} marker={chartData.marker} />
          </div>
        </article>

        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Signal summary</p>

          <div className="mt-6 space-y-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-ink-800/70">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">Confidence</p>
              <p className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{formatConfidence(signal.confidence)}</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-ink-800/70">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">Reference price</p>
              <p className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{formatPrice(signal.price)}</p>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-white/10 dark:bg-ink-800/70">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">Generated</p>
              <p className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">{formatSignalDate(signal.date)}</p>
            </div>
          </div>

          <div className="mt-6 rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5">
            <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Model note</p>
            <p className="mt-3 text-sm leading-7 text-slate-700 dark:text-slate-200">{signal.note}</p>
          </div>
        </article>
      </section>
    </div>
  );
}
