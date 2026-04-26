import Link from "next/link";

import { PublicFooter, PublicHeader, TickerBar } from "@/components/site/site-chrome";
import { ArrowRightIcon, CheckIcon } from "@/components/site/icons";
import { Button } from "@/components/ui/button";
import { pricingPlans, pricingComparisonRows } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

export default function PricingPage() {
  return (
    <main className="min-h-screen bg-bg-0 text-text-1">
      <TickerBar />
      <PublicHeader />

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-6 lg:px-10 lg:py-24">
        <div className="mx-auto max-w-3xl text-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-cyan/20 bg-cyan-dim px-4 py-2 text-[11px] uppercase tracking-[0.22em] text-cyan">
            <span className="h-2 w-2 rounded-full bg-cyan animate-pulse-soft" />
            Pricing
          </div>
          <h1 className="mt-7 font-display text-[clamp(42px,6vw,72px)] font-extrabold leading-[0.95] tracking-[-0.08em] text-white">
            Simple, transparent pricing
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-8 text-text-2">
            Three subscription tiers, one consistent dashboard experience. Compare the signal limits, strategy capacity,
            and premium features side by side.
          </p>
        </div>

        <div className="mt-14 grid gap-6 lg:grid-cols-3">
          {pricingPlans.map((plan) => (
            <article
              key={plan.tier}
              className={cn(
                "rounded-[20px] border p-6 shadow-glow transition",
                plan.highlighted
                  ? "order-first border-cyan/35 bg-[linear-gradient(180deg,rgba(0,200,212,0.10),rgba(17,23,32,0.85))] lg:order-none"
                  : "border-border bg-bg-1/80"
              )}
            >
              {plan.highlighted ? (
                <div className="mb-4 inline-flex rounded-full border border-cyan/25 bg-cyan-dim px-3 py-1 text-[10px] uppercase tracking-[0.22em] text-cyan">
                  Popular
                </div>
              ) : null}

              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{plan.tier}</div>
              <h2 className="mt-3 font-display text-2xl font-bold tracking-[-0.04em] text-white">{plan.name}</h2>
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
                      <CheckIcon className="h-3.5 w-3.5" />
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

        <section className="mt-16 rounded-[24px] border border-border bg-bg-1/80">
          <div className="flex flex-col gap-4 border-b border-border px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Feature matrix</div>
              <h3 className="mt-2 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Everything at a glance</h3>
            </div>
            <Button asChild variant="outlineGold" size="sm">
              <Link href="/auth/register">
                Upgrade now
                <ArrowRightIcon className="ml-2 h-4 w-4" />
              </Link>
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
                {pricingComparisonRows.map((row, index) => (
                  <tr key={row.feature} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
                    <td className="border-t border-border px-6 py-4 text-sm text-text-1">{row.feature}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row.free}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row.basic}</td>
                    <td className="border-t border-border px-6 py-4 font-mono text-sm text-text-2">{row.premium}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </section>

      <PublicFooter />
    </main>
  );
}
