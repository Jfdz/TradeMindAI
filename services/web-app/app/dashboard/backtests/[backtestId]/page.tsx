import { BacktestResults } from "@/components/dashboard/backtest-results";

export default function BacktestResultsPage({ params }: { params: { backtestId: string } }) {
  return (
    <div className="space-y-8">
      <section className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
        <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Backtest results</p>
        <h1 className="mt-3 text-3xl font-semibold text-slate-900 dark:text-white">Review the completed run</h1>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-600 dark:text-slate-300">
          Equity curve, drawdown, metrics, and trade history are grouped here so the dashboard can review an
          individual backtest without leaving the workspace.
        </p>
      </section>

      <BacktestResults backtestId={params.backtestId} />
    </div>
  );
}
