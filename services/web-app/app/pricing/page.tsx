import Link from "next/link";

import { Button } from "@/components/ui/button";

const plans = [
  {
    plan: "FREE",
    displayName: "Free Tier",
    price: "$0",
    description: "For experimenting with core signals and a single strategy.",
    maxSignalsPerDay: "5 signals/day",
    maxStrategies: "1 strategy",
    features: ["5 trading signals per day", "1 active strategy", "Basic market data"],
    cta: "Get started",
    highlight: false,
  },
  {
    plan: "BASIC",
    displayName: "Basic",
    price: "$19",
    description: "For active traders who want more signal volume and strategy capacity.",
    maxSignalsPerDay: "50 signals/day",
    maxStrategies: "5 strategies",
    features: ["50 trading signals per day", "5 active strategies", "Real-time market data", "Email notifications"],
    cta: "Choose Basic",
    highlight: true,
  },
  {
    plan: "PREMIUM",
    displayName: "Premium",
    price: "$49",
    description: "For power users who want unlimited breadth and the full feature set.",
    maxSignalsPerDay: "Unlimited",
    maxStrategies: "Unlimited",
    features: [
      "Unlimited trading signals",
      "Unlimited strategies",
      "Real-time market data",
      "Priority support",
      "Advanced AI predictions",
      "Backtesting engine",
    ],
    cta: "Go Premium",
    highlight: false,
  },
];

const comparisonRows = [
  ["Trading signals", "5/day", "50/day", "Unlimited"],
  ["Active strategies", "1", "5", "Unlimited"],
  ["Market data", "Basic", "Real-time", "Real-time"],
  ["AI predictions", "No", "Yes", "Advanced"],
  ["Backtesting", "No", "No", "Yes"],
];

export default function PricingPage() {
  return (
    <main className="min-h-screen bg-[#08121f] text-slate-50">
      <section className="relative isolate overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(246,208,138,0.18),_transparent_32%),radial-gradient(circle_at_20%_20%,_rgba(84,213,180,0.16),_transparent_28%),linear-gradient(180deg,_#0d1728_0%,_#08121f_100%)]" />

        <div className="relative mx-auto max-w-7xl px-6 py-8 lg:px-10">
          <header className="flex items-center justify-between border-b border-white/10 pb-5">
            <div>
              <p className="text-xs uppercase tracking-[0.45em] text-gold-300/80">TradeMindAI</p>
              <h1 className="mt-2 text-lg font-semibold tracking-wide text-white">Pricing</h1>
            </div>
            <nav className="flex items-center gap-3 text-sm text-slate-300">
              <Link className="transition hover:text-white" href="/">
                Home
              </Link>
              <Link className="transition hover:text-white" href="/auth/register">
                Register
              </Link>
            </nav>
          </header>

          <div className="py-16">
            <div className="max-w-3xl">
              <p className="inline-flex rounded-full border border-gold-300/30 bg-gold-300/10 px-4 py-1 text-xs font-medium uppercase tracking-[0.35em] text-gold-300">
                FEAT-15 PBI-02
              </p>
              <h2 className="mt-6 text-5xl font-semibold leading-tight text-white sm:text-6xl">
                Pick the plan that matches your trading tempo.
              </h2>
              <p className="mt-6 text-lg leading-8 text-slate-300">
                Three subscription tiers, one consistent dashboard experience. Compare the signal limits,
                strategy capacity, and premium features side by side.
              </p>
            </div>

            <div className="mt-12 grid gap-6 lg:grid-cols-3">
              {plans.map((plan) => (
                <article
                  key={plan.plan}
                  className={`rounded-[2rem] border p-6 shadow-glow backdrop-blur ${
                    plan.highlight ? "border-gold-300/40 bg-gold-300/10" : "border-white/10 bg-white/5"
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-xs uppercase tracking-[0.35em] text-slate-400">{plan.plan}</p>
                      <h3 className="mt-2 text-2xl font-semibold text-white">{plan.displayName}</h3>
                    </div>
                    {plan.highlight ? (
                      <span className="rounded-full bg-gold-300 px-3 py-1 text-xs font-semibold uppercase tracking-[0.25em] text-ink-950">
                        Popular
                      </span>
                    ) : null}
                  </div>

                  <p className="mt-4 text-sm leading-7 text-slate-300">{plan.description}</p>
                  <div className="mt-6 flex items-end gap-2">
                    <span className="text-5xl font-semibold text-white">{plan.price}</span>
                    <span className="pb-1 text-sm text-slate-400">/month</span>
                  </div>

                  <div className="mt-6 space-y-2 rounded-3xl border border-white/10 bg-ink-800/70 p-4 text-sm text-slate-200">
                    <p>{plan.maxSignalsPerDay}</p>
                    <p>{plan.maxStrategies}</p>
                  </div>

                  <ul className="mt-6 space-y-3 text-sm leading-6 text-slate-200">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-3">
                        <span className="mt-2 h-2 w-2 rounded-full bg-mint-400" />
                        <span>{feature}</span>
                      </li>
                    ))}
                  </ul>

                  <div className="mt-8">
                    <Button asChild className="w-full">
                      <Link href="/auth/register">{plan.cta}</Link>
                    </Button>
                  </div>
                </article>
              ))}
            </div>

            <section className="mt-12 rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Feature matrix</p>
                  <h3 className="mt-2 text-2xl font-semibold text-white">Everything at a glance</h3>
                </div>
                <Button asChild variant="outline">
                  <Link href="/auth/register">Upgrade now</Link>
                </Button>
              </div>

              <div className="mt-6 overflow-x-auto">
                <table className="min-w-full border-separate border-spacing-y-3 text-left text-sm">
                  <thead>
                    <tr className="text-slate-400">
                      <th className="px-4 py-2">Feature</th>
                      <th className="px-4 py-2">FREE</th>
                      <th className="px-4 py-2">BASIC</th>
                      <th className="px-4 py-2">PREMIUM</th>
                    </tr>
                  </thead>
                  <tbody>
                    {comparisonRows.map((row) => (
                      <tr key={row[0]} className="rounded-2xl bg-ink-800/70 text-slate-200">
                        <td className="rounded-l-2xl px-4 py-3 font-medium text-white">{row[0]}</td>
                        <td className="px-4 py-3">{row[1]}</td>
                        <td className="px-4 py-3">{row[2]}</td>
                        <td className="rounded-r-2xl px-4 py-3">{row[3]}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </section>
    </main>
  );
}
