import { BacktestForm } from "@/components/dashboard/backtest-form";

const highlights = [
  { label: "Validated inputs", value: "Symbol, dates, capital" },
  { label: "Strategy sizing", value: "Allocation-aware quantity" },
  { label: "Async flow", value: "Accepted job response" },
];

export default function BacktestsPage() {
  return (
    <div className="space-y-8">
      <section className="grid gap-4 md:grid-cols-3">
        {highlights.map((item) => (
          <article key={item.label} className="rounded-3xl border border-slate-200 bg-slate-100 p-5 shadow-glow dark:border-white/10 dark:bg-white/5">
            <p className="text-xs uppercase tracking-[0.35em] text-slate-500 dark:text-slate-400">{item.label}</p>
            <p className="mt-3 text-lg font-semibold text-slate-900 dark:text-white">{item.value}</p>
          </article>
        ))}
      </section>

      <BacktestForm />
    </div>
  );
}
