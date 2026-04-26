import Link from "next/link";

import { HeroChartPanel } from "@/components/site/hero-chart-panel";
import { PublicFooter, PublicHeader, TickerBar } from "@/components/site/site-chrome";
import { Button } from "@/components/ui/button";
import {
  ArrowRightIcon,
  BellIcon,
  ChartIcon,
  FlaskIcon,
  LockIcon,
  TargetIcon,
  ZapIcon,
} from "@/components/site/icons";
import { landingFeatures, pricingPlans } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

const iconMap = {
  zap: ZapIcon,
  target: TargetIcon,
  chart: ChartIcon,
  flask: FlaskIcon,
  bell: BellIcon,
  lock: LockIcon,
} as const;

const heroStats = [
  { label: "Signals generated", value: "12,400+" },
  { label: "Model accuracy (30d)", value: "94.2%" },
  { label: "Volume tracked", value: "$2.8B" },
  { label: "Active traders", value: "4,200+" },
];

export default function HomePage() {
  return (
    <main className="min-h-screen bg-bg-0 text-text-1">
      <TickerBar />
      <PublicHeader />

      <section className="relative">
        <div className="mx-auto max-w-7xl px-5 pb-16 pt-20 sm:px-6 lg:px-10 lg:pb-24 lg:pt-24">
          <div className="grid gap-14 lg:grid-cols-[0.95fr_1.05fr] lg:items-center">
            <div className="max-w-2xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-cyan/20 bg-cyan-dim px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
                <span className="h-2 w-2 rounded-full bg-cyan animate-pulse-soft" />
                AI-Powered Signal Engine
              </div>

              <h1 className="mt-7 max-w-3xl font-display text-[clamp(42px,6vw,80px)] font-extrabold leading-[0.95] tracking-[-0.08em] text-white">
                Trading intelligence with a <span className="text-cyan">sharper edge</span>
              </h1>
              <div className="mt-5 h-1 w-36 rounded-full bg-gradient-to-r from-cyan via-cyan to-transparent" />

              <p className="mt-7 max-w-xl text-[18px] leading-8 text-text-2">
                TradeMindAI turns market predictions into disciplined decisions. Review live signals, manage strategies,
                and move from insight to execution.
              </p>

              <div className="mt-9 flex flex-col gap-3 sm:flex-row">
                <Button asChild size="xl" variant="cyan" className="sm:min-w-52">
                  <Link href="/auth/register">
                    Start for Free
                    <ArrowRightIcon className="ml-2 h-4 w-4" />
                  </Link>
                </Button>
                <Button asChild size="xl" variant="outline" className="sm:min-w-48">
                  <Link href="/pricing">Compare Plans</Link>
                </Button>
              </div>
            </div>

            <div className="lg:pl-4">
              <HeroChartPanel />
            </div>
          </div>

          <div className="mt-10 grid grid-cols-2 border-t border-border pt-6 text-left md:grid-cols-4">
            {heroStats.map((stat) => (
              <div key={stat.label} className="border-b border-border px-0 py-4 md:border-b-0 md:border-r md:px-5">
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{stat.label}</div>
                <div className="mt-3 font-display text-2xl font-bold tracking-[-0.04em] text-white">{stat.value}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-6 sm:px-6 lg:px-10 lg:py-10">
        <div className="grid gap-0 overflow-hidden rounded-[24px] border border-border bg-bg-1/80 md:grid-cols-2 xl:grid-cols-3">
          {landingFeatures.map((feature, index) => {
            const Icon = iconMap[feature.icon as keyof typeof iconMap];

            return (
              <article
                key={feature.title}
                className={cn(
                  "group border-border p-6 transition hover:bg-white/[0.02]",
                  index < 3 ? "md:border-b" : "",
                  index % 2 === 0 ? "md:border-r" : "",
                  index < 5 ? "xl:border-b" : ""
                )}
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-cyan/20 bg-cyan-dim text-cyan">
                  <Icon className="h-5 w-5" />
                </div>
                <h2 className="mt-4 font-display text-lg font-semibold tracking-[-0.03em] text-white">{feature.title}</h2>
                <p className="mt-3 max-w-sm text-sm leading-7 text-text-2">{feature.description}</p>
              </article>
            );
          })}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-16 sm:px-6 lg:px-10 lg:py-24">
        <div className="flex flex-col gap-4 border-b border-border pb-8">
          <div className="inline-flex items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
            <span className="h-2 w-2 rounded-full bg-cyan animate-pulse-soft" />
            Pricing preview
          </div>
          <h2 className="font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
            Pick the plan that matches your trading tempo
          </h2>
          <p className="max-w-2xl text-sm leading-7 text-text-2">
            Three subscription tiers, one consistent dashboard experience. Compare signal limits, strategy capacity,
            and premium features side by side.
          </p>
        </div>

        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {pricingPlans.map((plan) => (
            <article
              key={plan.tier}
              className={cn(
                "rounded-[18px] border p-6 shadow-glow transition",
                plan.highlighted
                  ? "border-cyan/35 bg-[linear-gradient(180deg,rgba(0,200,212,0.10),rgba(17,23,32,0.85))]"
                  : "border-border bg-bg-1/80"
              )}
            >
              {plan.highlighted ? (
                <div className="mb-4 inline-flex rounded-full border border-cyan/25 bg-cyan-dim px-3 py-1 text-[10px] uppercase tracking-[0.22em] text-cyan">
                  Popular
                </div>
              ) : null}
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{plan.tier}</div>
              <h3 className="mt-3 font-display text-2xl font-bold tracking-[-0.04em] text-white">{plan.name}</h3>
              <div className="mt-5 flex items-end gap-2">
                <span className="font-mono text-[40px] leading-none text-white">{plan.price}</span>
                <span className="pb-1 text-sm text-text-2">/month</span>
              </div>
              <p className="mt-3 font-mono text-sm text-cyan">{plan.tagline}</p>

              <div className="my-6 h-px bg-border" />

              <ul className="space-y-3 text-sm text-text-1">
                {plan.features.map((feature) => (
                  <li key={feature} className="flex items-start gap-3">
                    <span className="mt-1 inline-flex h-5 w-5 items-center justify-center rounded-full bg-cyan-dim text-cyan">
                      <ArrowRightIcon className="h-3 w-3 rotate-[-45deg]" />
                    </span>
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>

              <Button asChild className="mt-8 w-full" size="lg" variant={plan.highlighted ? "cyan" : "outlineCyan"}>
                <Link href="/auth/register">{plan.cta}</Link>
              </Button>
            </article>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-2 pb-20 sm:px-6 lg:px-10">
        <div className="overflow-hidden rounded-[24px] border border-border bg-bg-1/80">
          <div className="flex items-center justify-between gap-4 border-b border-border px-6 py-5">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Pricing comparison</div>
              <h3 className="mt-2 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Everything at a glance</h3>
            </div>
            <Button asChild variant="outlineGold" size="sm">
              <Link href="/auth/register">Upgrade now</Link>
            </Button>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-[720px] w-full border-separate border-spacing-0 text-left">
              <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                <tr>
                  <th className="px-6 py-4">Feature</th>
                  <th className="px-6 py-4">Free</th>
                  <th className="px-6 py-4">Basic</th>
                  <th className="px-6 py-4">Premium</th>
                </tr>
              </thead>
              <tbody>
                {[
                  ["Trading signals", "5", "50", "∞"],
                  ["Active strategies", "1", "5", "∞"],
                  ["Market data", "Basic", "Real-time", "Real-time"],
                  ["AI predictions", "No", "Yes", "Advanced"],
                  ["Backtesting", "No", "No", "Yes"],
                  ["Notifications", "Email", "Email", "Email + push"],
                  ["Support", "Community", "Priority", "Priority"],
                  ["Retention", "30d", "90d", "Full"],
                ].map((row, index) => (
                  <tr key={row[0]} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
                    <td className="border-t border-border px-6 py-4 text-sm text-text-1">{row[0]}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row[1]}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row[2]}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row[3]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <PublicFooter />
    </main>
  );
}
