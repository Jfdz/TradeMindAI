import type { Metadata } from "next";
import Link from "next/link";

import { TickerBar } from "@/components/site/ticker-bar";
import { PublicFooter, PublicHeader } from "@/components/site/site-chrome";
import { Button } from "@/components/ui/button";
import { ArrowRightIcon, ZapIcon, TargetIcon, ChartIcon } from "@/components/site/icons";

export const metadata: Metadata = {
  title: "About",
  description:
    "TradeMindAI is an AI-powered trading signal platform built for disciplined traders. Learn how our signal engine, portfolio tools, and backtesting work together.",
};

const steps = [
  {
    number: "01",
    icon: ZapIcon,
    title: "Signals generated",
    description:
      "Our AI engine analyses market data across multiple timeframes and publishes BUY, SELL, and HOLD signals with a confidence score, entry price, take-profit, and stop-loss — automatically.",
  },
  {
    number: "02",
    icon: ChartIcon,
    title: "You review and decide",
    description:
      "Every signal surfaces in the dashboard with full context: the reasoning behind it, the LLM analysis of current news, and the historical performance of the underlying strategy.",
  },
  {
    number: "03",
    icon: TargetIcon,
    title: "Track execution",
    description:
      "Log your positions, track unrealized P&L against live prices, and close trades to build a permanent history. Run backtests to validate strategies before committing capital.",
  },
];

export default function AboutPage() {
  return (
    <main className="min-h-screen bg-bg-0 text-text-1">
      <TickerBar />
      <PublicHeader />

      {/* Hero */}
      <section className="mx-auto max-w-7xl px-5 pb-16 pt-20 sm:px-6 lg:px-10 lg:pb-24 lg:pt-28">
        <div className="max-w-3xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-cyan/20 bg-cyan-dim px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
            <span className="h-2 w-2 rounded-full bg-cyan animate-pulse-soft" />
            About TradeMindAI
          </div>
          <h1 className="mt-7 font-display text-[clamp(42px,6vw,72px)] font-extrabold leading-[0.95] tracking-[-0.08em] text-white">
            Trading intelligence built for <span className="text-cyan">discipline</span>
          </h1>
          <div className="mt-5 h-1 w-36 rounded-full bg-gradient-to-r from-cyan via-cyan to-transparent" />
          <p className="mt-7 max-w-2xl text-lg leading-8 text-text-2">
            TradeMindAI is a signal-first trading platform. We combine quantitative market models with large language
            model reasoning to deliver signals you can inspect, track, and act on — without the noise.
          </p>
        </div>
      </section>

      {/* How it works */}
      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-6 lg:px-10 lg:py-16">
        <div className="mb-12">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">How it works</div>
          <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
            From signal to execution
          </h2>
        </div>

        <div className="grid gap-0 overflow-hidden rounded-[24px] border border-border bg-bg-1/80 lg:grid-cols-3">
          {steps.map((step, index) => {
            const Icon = step.icon;
            return (
              <div
                key={step.number}
                className={`p-8 ${index < steps.length - 1 ? "border-b border-border lg:border-b-0 lg:border-r" : ""}`}
              >
                <div className="flex items-start gap-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-cyan/20 bg-cyan-dim text-cyan">
                    <Icon className="h-5 w-5" />
                  </div>
                  <span className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3 pt-2.5">{step.number}</span>
                </div>
                <h3 className="mt-5 font-display text-lg font-semibold tracking-[-0.03em] text-white">{step.title}</h3>
                <p className="mt-3 text-sm leading-7 text-text-2">{step.description}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Risk disclosure */}
      <section id="risk" className="mx-auto max-w-7xl px-5 py-12 sm:px-6 lg:px-10 lg:py-16">
        <div className="rounded-[24px] border border-border bg-bg-1/80 p-8 lg:p-10">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Risk disclosure</div>
          <h2 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">
            Important information about trading risks
          </h2>
          <div className="mt-6 space-y-4 text-sm leading-7 text-text-2">
            <p>
              Trading financial instruments, including stocks, ETFs, currencies, and cryptocurrencies, carries a
              significant risk of loss and is not suitable for all investors. Past performance of any signal, strategy,
              or backtested result is not indicative of future results.
            </p>
            <p>
              TradeMindAI provides information, signals, and analytical tools for educational and informational purposes
              only. Nothing on this platform constitutes financial advice, investment advice, trading advice, or any other
              sort of advice. You should not treat any of the platform&apos;s content as such.
            </p>
            <p>
              TradeMindAI does not recommend that any financial instrument should be bought, sold, or held by you. You
              should conduct your own due diligence and consult your financial advisor before making any investment
              decision.
            </p>
            <p>
              AI-generated signals are produced by probabilistic models and may be incorrect. Confidence scores reflect
              model certainty, not guaranteed outcomes. You bear full responsibility for any trading decisions you make
              based on information provided by this platform.
            </p>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="mx-auto max-w-7xl px-5 py-12 pb-24 sm:px-6 lg:px-10">
        <div className="flex flex-col items-start gap-6 rounded-[24px] border border-border bg-bg-1/80 p-8 sm:flex-row sm:items-center sm:justify-between lg:p-10">
          <div>
            <h2 className="font-display text-2xl font-bold tracking-[-0.04em] text-white">Ready to start?</h2>
            <p className="mt-2 text-sm text-text-2">Free plan includes 5 signals per day. No credit card required.</p>
          </div>
          <div className="flex shrink-0 flex-col gap-3 sm:flex-row">
            <Button asChild size="lg" variant="cyan">
              <Link href="/auth/register">
                Start for Free
                <ArrowRightIcon className="ml-2 h-4 w-4" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/pricing">Compare Plans</Link>
            </Button>
          </div>
        </div>
      </section>

      <PublicFooter />
    </main>
  );
}
