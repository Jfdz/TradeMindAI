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
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtest report</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Review the completed run
        </h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-text-2">
          Equity curve, drawdown, metrics, and trade history are grouped here so the dashboard can review an individual
          backtest without leaving the workspace.
        </p>
      </section>

      <BacktestResults backtestId={backtestId} />
    </div>
  );
}
