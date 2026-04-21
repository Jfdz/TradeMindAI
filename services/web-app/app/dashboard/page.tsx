const summaryCards = [
  { label: "Total signals", value: "128", detail: "+18 this week", tone: "text-amber-600 dark:text-gold-300" },
  { label: "Portfolio value", value: "$82.4k", detail: "+4.8% today", tone: "text-slate-900 dark:text-white" },
  { label: "Top performer", value: "AAPL", detail: "+12.4% unrealized", tone: "text-emerald-600 dark:text-mint-300" },
  { label: "Win rate", value: "61%", detail: "Last 30 days", tone: "text-amber-600 dark:text-gold-300" },
];

const recentSignals = [
  { symbol: "AAPL", type: "BUY", confidence: "92%", price: "$178.50", timeframe: "1D" },
  { symbol: "NVDA", type: "BUY", confidence: "88%", price: "$846.20", timeframe: "4H" },
  { symbol: "MSFT", type: "HOLD", confidence: "74%", price: "$421.10", timeframe: "1D" },
  { symbol: "TSLA", type: "SELL", confidence: "81%", price: "$187.80", timeframe: "4H" },
  { symbol: "AMD", type: "BUY", confidence: "79%", price: "$171.25", timeframe: "1D" },
];

const activityNotes = [
  "Risk limits remain within the configured strategy envelope.",
  "Premium plan unlocked unlimited strategies and backtesting tools.",
  "Last signal batch generated 5 minutes ago from the AI engine.",
];

export default function DashboardHomePage() {
  return (
    <div className="space-y-8">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summaryCards.map((card) => (
          <article key={card.label} className="rounded-3xl border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
            <p className="text-xs uppercase tracking-[0.35em] text-slate-500 dark:text-slate-400">{card.label}</p>
            <p className={`mt-4 text-4xl font-semibold ${card.tone}`}>{card.value}</p>
            <p className="mt-3 text-sm text-slate-600 dark:text-slate-300">{card.detail}</p>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Recent signals</p>
              <h3 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Last five signals in the workspace</h3>
            </div>
            <span className="rounded-full border border-slate-200 bg-slate-100 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-600 dark:border-white/10 dark:bg-white/5 dark:text-slate-300">
              Live feed
            </span>
          </div>

          <div className="mt-6 space-y-4">
            {recentSignals.map((signal) => (
              <div
                key={`${signal.symbol}-${signal.timeframe}`}
                className="grid gap-3 rounded-2xl border border-slate-200 bg-slate-100 p-4 sm:grid-cols-[1fr_auto_auto_auto] dark:border-white/10 dark:bg-ink-800/70"
              >
                <div>
                  <p className="text-sm font-semibold text-slate-900 dark:text-white">{signal.symbol}</p>
                  <p className="text-xs uppercase tracking-[0.3em] text-slate-500 dark:text-slate-400">{signal.timeframe}</p>
                </div>
                <div className="text-sm text-slate-700 dark:text-slate-200">{signal.type}</div>
                <div className="text-sm text-slate-700 dark:text-slate-200">{signal.confidence} confidence</div>
                <div className="text-sm text-slate-700 dark:text-slate-200">{signal.price}</div>
              </div>
            ))}
          </div>
        </article>

        <article className="rounded-[2rem] border border-slate-200 bg-slate-100 p-6 shadow-glow dark:border-white/10 dark:bg-white/5">
          <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Workspace notes</p>
          <h3 className="mt-3 text-2xl font-semibold text-slate-900 dark:text-white">Overview details</h3>

          <div className="mt-6 space-y-4">
            {activityNotes.map((item) => (
              <div key={item} className="rounded-2xl border border-slate-200 bg-slate-100 p-4 text-sm text-slate-700 dark:border-white/10 dark:bg-ink-800/70 dark:text-slate-200">
                {item}
              </div>
            ))}
          </div>

          <div className="mt-6 rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5">
            <p className="text-xs uppercase tracking-[0.35em] text-amber-600 dark:text-gold-300/80">Next actions</p>
            <p className="mt-3 text-sm leading-7 text-slate-700 dark:text-slate-200">
              Signals, portfolio, backtest, and settings pages can now be layered onto the dashboard shell in the
              FEAT-16 follow-up PBIs.
            </p>
          </div>
        </article>
      </section>
    </div>
  );
}
