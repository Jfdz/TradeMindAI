const highlights = [
  {
    title: "Signals that matter",
    copy: "Review AI-generated signals with confidence, timeframe, and risk context in one place.",
  },
  {
    title: "Strategy controls",
    copy: "Create strategies, tune risk parameters, and keep every portfolio decision user-owned.",
  },
  {
    title: "Execution ready",
    copy: "A scaffold built for auth, charts, dashboards, and the next frontend PBI slices.",
  },
];

const metrics = [
  { label: "Live signals", value: "24" },
  { label: "Avg confidence", value: "87%" },
  { label: "Watchlists", value: "08" },
];

export default function Home() {
  return (
    <main className="min-h-screen overflow-hidden bg-[#08121f] text-slate-50">
      <div className="relative isolate">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(246,208,138,0.18),_transparent_32%),radial-gradient(circle_at_20%_20%,_rgba(84,213,180,0.16),_transparent_28%),linear-gradient(180deg,_#0d1728_0%,_#08121f_100%)]" />
        <div className="pointer-events-none absolute -left-24 top-24 h-72 w-72 rounded-full bg-gold-400/15 blur-3xl" />
        <div className="pointer-events-none absolute right-0 top-1/3 h-80 w-80 rounded-full bg-mint-400/10 blur-3xl" />

        <div className="relative mx-auto flex min-h-screen max-w-7xl flex-col px-6 py-8 lg:px-10">
          <header className="flex items-center justify-between border-b border-white/10 pb-5">
            <div>
              <p className="text-xs uppercase tracking-[0.45em] text-gold-300/80">TradeMindAI</p>
              <h1 className="mt-2 text-lg font-semibold tracking-wide text-white">Frontend scaffold</h1>
            </div>
            <div className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm text-slate-200 shadow-glow">
              Next.js App Router
            </div>
          </header>

          <section className="grid flex-1 gap-10 py-12 lg:grid-cols-[1.2fr_0.8fr] lg:items-center">
            <div className="max-w-3xl">
              <p className="inline-flex rounded-full border border-gold-300/30 bg-gold-300/10 px-4 py-1 text-xs font-medium uppercase tracking-[0.35em] text-gold-300">
                FEAT-14 PBI-01
              </p>
              <h2 className="mt-6 max-w-2xl text-5xl font-semibold leading-tight text-white sm:text-6xl">
                A clean foundation for the trading dashboard.
              </h2>
              <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-300">
                This scaffold sets up the Next.js app, Tailwind pipeline, and a bold landing surface that can
                evolve into auth, signals, portfolio, and backtest flows.
              </p>

              <div className="mt-10 flex flex-wrap gap-4">
                <a
                  className="rounded-full bg-gold-400 px-6 py-3 text-sm font-semibold text-ink-950 transition hover:bg-gold-300"
                  href="#highlights"
                >
                  Explore scaffold
                </a>
                <a
                  className="rounded-full border border-white/15 bg-white/5 px-6 py-3 text-sm font-semibold text-white transition hover:border-white/30 hover:bg-white/10"
                  href="#metrics"
                >
                  View metrics
                </a>
              </div>
            </div>

              <div className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
              <div className="grid gap-4 sm:grid-cols-3" id="metrics">
                {metrics.map((metric) => (
                  <div key={metric.label} className="rounded-2xl border border-white/10 bg-ink-800/70 p-4">
                    <p className="text-xs uppercase tracking-[0.3em] text-slate-400">{metric.label}</p>
                    <p className="mt-3 text-3xl font-semibold text-white">{metric.value}</p>
                  </div>
                ))}
              </div>

              <div className="mt-6 rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5">
                <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Sprint focus</p>
                <div className="mt-4 space-y-3 text-sm leading-6 text-slate-200">
                  <p>1. App Router scaffold with typed configs.</p>
                  <p>2. Distinct visual language for the trading product.</p>
                  <p>3. Ready for auth and API client work in the next PBI.</p>
                </div>
              </div>
            </div>
          </section>

          <section id="highlights" className="grid gap-4 border-t border-white/10 py-10 md:grid-cols-3">
            {highlights.map((item) => (
              <article
                key={item.title}
                className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur"
              >
                <h3 className="text-xl font-semibold text-white">{item.title}</h3>
                <p className="mt-3 text-sm leading-7 text-slate-300">{item.copy}</p>
              </article>
            ))}
          </section>
        </div>
      </div>
    </main>
  );
}
