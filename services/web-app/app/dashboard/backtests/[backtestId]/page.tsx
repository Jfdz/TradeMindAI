import { BacktestResults } from "@/components/dashboard/backtest-results";

type BacktestResultsPageProps = {
  params: Promise<{
    backtestId: string;
  }>;
};

export default async function BacktestResultsPage({ params }: BacktestResultsPageProps) {
  const { backtestId } = await params;

  return (
    <div className="space-y-8">
      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
        <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Backtest results</p>
        <h1 className="mt-3 text-3xl font-semibold text-white">Review the completed run</h1>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-300">
          Equity curve, drawdown, metrics, and trade history are grouped here so the dashboard can review an
          individual backtest without leaving the workspace.
        </p>
      </section>

      <BacktestResults backtestId={backtestId} />
    </div>
  );
}
