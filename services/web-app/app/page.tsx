import Link from "next/link";

import { Button } from "@/components/ui/button";

const featureCards = [
  {
    title: "Signals that move fast",
    copy: "Track AI-generated BUY, SELL, and HOLD signals with clear confidence, timeframe, and stop-loss context.",
  },
  {
    title: "Strategy-first workflow",
    copy: "Create user-owned strategies, tune risk parameters, and keep every trade decision tied to the right account.",
  },
  {
    title: "Built for the dashboard",
    copy: "The landing page routes visitors into auth, subscriptions, and the dashboard experience already scaffolded in FEAT-14.",
  },
];

const socialProof = [
  { label: "Signals processed", value: "12k+" },
  { label: "Strategy plans", value: "3 tiers" },
  { label: "Frontend load", value: "< 2s LCP" },
];

const trustLogos = ["Momentum desk", "Alpha control", "Risk layer", "Signal hub"];

export default function HomePage() {
  return (
    <main className="min-h-screen bg-[#08121f] text-slate-50">
      <section className="relative isolate overflow-hidden">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,_rgba(246,208,138,0.18),_transparent_32%),radial-gradient(circle_at_20%_20%,_rgba(84,213,180,0.16),_transparent_28%),linear-gradient(180deg,_#0d1728_0%,_#08121f_100%)]" />
        <div className="pointer-events-none absolute -left-24 top-24 h-72 w-72 rounded-full bg-gold-400/15 blur-3xl" />
        <div className="pointer-events-none absolute right-0 top-1/3 h-80 w-80 rounded-full bg-mint-400/10 blur-3xl" />

        <div className="relative mx-auto max-w-7xl px-6 py-8 lg:px-10">
          <header className="flex items-center justify-between border-b border-white/10 pb-5">
            <div>
              <p className="text-xs uppercase tracking-[0.45em] text-gold-300/80">TradeMindAI</p>
              <h1 className="mt-2 text-lg font-semibold tracking-wide text-white">Landing page</h1>
            </div>
            <nav className="flex items-center gap-3 text-sm text-slate-300">
              <a className="transition hover:text-white" href="/pricing">
                Pricing
              </a>
              <a className="transition hover:text-white" href="/auth/login">
                Sign in
              </a>
            </nav>
          </header>

          <div className="grid gap-14 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
            <div className="max-w-3xl">
              <p className="inline-flex rounded-full border border-gold-300/30 bg-gold-300/10 px-4 py-1 text-xs font-medium uppercase tracking-[0.35em] text-gold-300">
                FEAT-15 PBI-01
              </p>
              <h2 className="mt-6 text-5xl font-semibold leading-tight text-white sm:text-6xl">
                Trading intelligence with a sharper edge.
              </h2>
              <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-300">
                TradeMindAI turns market predictions into disciplined decisions. Review live signals, manage
                strategies, and move from insight to execution with a design built for speed.
              </p>

              <div className="mt-10 flex flex-wrap gap-4">
                <Button asChild>
                  <Link href="/auth/register">Start free</Link>
                </Button>
                <Button asChild variant="outline">
                  <Link href="/pricing">Compare plans</Link>
                </Button>
              </div>

              <div className="mt-10 grid gap-4 sm:grid-cols-3">
                {socialProof.map((item) => (
                  <div key={item.label} className="rounded-3xl border border-white/10 bg-white/5 p-5 shadow-glow">
                    <p className="text-xs uppercase tracking-[0.3em] text-slate-400">{item.label}</p>
                    <p className="mt-3 text-3xl font-semibold text-white">{item.value}</p>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
              <div className="grid gap-4">
                <div className="rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5">
                  <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">What you get</p>
                  <div className="mt-4 space-y-3 text-sm leading-6 text-slate-200">
                    <p>Real-time signal view with confidence and risk context.</p>
                    <p>Strategy controls for user-owned trading plans.</p>
                    <p>Route-protected dashboard ready for the next FEAT slices.</p>
                  </div>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  {trustLogos.map((label) => (
                    <div key={label} className="rounded-2xl border border-white/10 bg-ink-800/70 px-4 py-3 text-sm text-slate-200">
                      {label}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <section className="grid gap-4 border-t border-white/10 py-10 md:grid-cols-3">
            {featureCards.map((item) => (
              <article key={item.title} className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow backdrop-blur">
                <h3 className="text-xl font-semibold text-white">{item.title}</h3>
                <p className="mt-3 text-sm leading-7 text-slate-300">{item.copy}</p>
              </article>
            ))}
          </section>
        </div>
      </section>
    </main>
  );
}
