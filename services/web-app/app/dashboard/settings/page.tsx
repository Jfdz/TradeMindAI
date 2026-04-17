"use client";

import Link from "next/link";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { useSession } from "next-auth/react";

import { Button } from "@/components/ui/button";

export default function SettingsPage() {
  const { data: session } = useSession();
  const [name, setName] = useState("TradeMind Operator");
  const [email, setEmail] = useState("user@tradermind.ai");
  const [timezone, setTimezone] = useState("Europe/Madrid");
  const [status, setStatus] = useState("Changes are saved locally until the backend profile API is connected.");

  useEffect(() => {
    if (session?.user?.email) {
      setEmail(session.user.email);
    }

    if (session?.user?.name) {
      setName(session.user.name);
    }
  }, [session]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus("Profile changes saved locally. Backend persistence is pending the next integration step.");
  }

  return (
    <div className="space-y-8">
      <section className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <article className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Profile</p>
          <h1 className="mt-3 text-3xl font-semibold text-white">Update your workspace details</h1>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300">
            Keep the dashboard identity aligned with the active account and settings that matter for the trading
            workflow.
          </p>

          <form className="mt-8 grid gap-5" onSubmit={handleSubmit}>
            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Display name</span>
              <input
                className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-white outline-none focus:border-gold-300/40"
                onChange={(event) => setName(event.target.value)}
                value={name}
              />
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Email</span>
              <input
                className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-slate-400 outline-none"
                disabled
                value={email}
              />
            </label>

            <label className="block">
              <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Timezone</span>
              <select
                className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-white outline-none focus:border-gold-300/40"
                onChange={(event) => setTimezone(event.target.value)}
                value={timezone}
              >
                <option value="Europe/Madrid">Europe/Madrid</option>
                <option value="UTC">UTC</option>
                <option value="America/New_York">America/New_York</option>
                <option value="Asia/Singapore">Asia/Singapore</option>
              </select>
            </label>

            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit">Save changes</Button>
              <p className="text-sm text-slate-300">{status}</p>
            </div>
          </form>
        </article>

        <article className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Subscription</p>
          <h2 className="mt-3 text-2xl font-semibold text-white">Current plan and upgrade path</h2>

          <div className="mt-6 rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-5">
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Current subscription</p>
            <p className="mt-3 text-3xl font-semibold text-white">Premium</p>
            <p className="mt-2 text-sm text-slate-200">Unlimited strategies, backtesting, and signal dashboards.</p>
            <p className="mt-4 text-xs uppercase tracking-[0.3em] text-slate-400">Renews on May 1, 2026</p>
          </div>

          <div className="mt-6 space-y-3">
            <div className="rounded-2xl border border-white/10 bg-ink-800/70 p-4 text-sm text-slate-200">
              Profile settings remain synchronized with the authenticated dashboard session.
            </div>
            <div className="rounded-2xl border border-white/10 bg-ink-800/70 p-4 text-sm text-slate-200">
              Trading limits and subscription gates are enforced by the backend services.
            </div>
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild>
              <Link href="/pricing">Upgrade plan</Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/dashboard">Back to dashboard</Link>
            </Button>
          </div>
        </article>
      </section>
    </div>
  );
}
