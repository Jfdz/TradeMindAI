import { BacktestForm } from "@/components/dashboard/backtest-form";

export default function BacktestsPage() {
  return (
    <div className="space-y-6">
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Backtests</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Run, inspect, and compare strategy performance
        </h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-text-2">
          Configure a backtest, watch the run animation, and review the metrics, equity curve, and trade summary once
          the job resolves.
        </p>
      </section>

      <BacktestForm />
    </div>
  );
}
