"use client";

import type { ReactNode } from "react";
import { useState } from "react";
import { signOut, useSession } from "next-auth/react";
import Link from "next/link";

import { Button } from "@/components/ui/button";

const navItems = [
  { href: "/dashboard", label: "Overview" },
  { href: "/dashboard/signals", label: "Signals" },
  { href: "/dashboard/portfolio", label: "Portfolio" },
  { href: "/dashboard/backtests", label: "Backtest" },
  { href: "/dashboard/settings", label: "Settings" },
];

export function DashboardShell({ children }: { children: ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { data: session } = useSession();

  return (
    <div className="min-h-screen bg-[#08121f] text-slate-50">
      <div className="grid min-h-screen lg:grid-cols-[280px_1fr]">
        <aside
          className={`border-r border-white/10 bg-[#0d1728]/95 px-5 py-6 backdrop-blur transition-transform lg:translate-x-0 ${
            sidebarOpen ? "translate-x-0" : "-translate-x-full"
          } fixed inset-y-0 left-0 z-40 w-[280px] lg:static`}
        >
          <div className="flex items-center justify-between pb-6">
            <div>
              <p className="text-xs uppercase tracking-[0.45em] text-gold-300/80">TradeMindAI</p>
              <h1 className="mt-2 text-lg font-semibold">Dashboard</h1>
            </div>
            <button
              className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xs uppercase tracking-[0.3em] text-slate-300 lg:hidden"
              onClick={() => setSidebarOpen(false)}
              type="button"
            >
              Close
            </button>
          </div>

          <nav className="space-y-2">
            {navItems.map((item) => (
              <Link
                key={item.href}
                className="flex items-center rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-slate-200 transition hover:border-gold-300/30 hover:bg-gold-300/10 hover:text-white"
                href={item.href}
                onClick={() => setSidebarOpen(false)}
              >
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="mt-8 rounded-3xl border border-gold-300/20 bg-gradient-to-br from-gold-300/10 to-mint-400/10 p-4">
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Session</p>
            <p className="mt-3 text-sm text-slate-200">{session?.user?.email ?? "Authenticated user"}</p>
          </div>
        </aside>

        {sidebarOpen ? (
          <button
            aria-label="Close navigation"
            className="fixed inset-0 z-30 bg-black/60 lg:hidden"
            onClick={() => setSidebarOpen(false)}
            type="button"
          />
        ) : null}

        <div className="relative flex min-h-screen flex-col">
          <header className="flex items-center justify-between border-b border-white/10 bg-[#08121f]/90 px-6 py-4 backdrop-blur lg:px-10">
            <div className="flex items-center gap-3">
              <button
                className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-200 lg:hidden"
                onClick={() => setSidebarOpen(true)}
                type="button"
              >
                Menu
              </button>
              <div>
                <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Dashboard core</p>
                <h2 className="mt-1 text-lg font-semibold text-white">Welcome back</h2>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm text-slate-200">
                {session?.user?.email ?? "user@tradermind.ai"}
              </div>
              <Button
                onClick={() => signOut({ callbackUrl: "/" })}
                type="button"
                variant="outline"
                className="hidden sm:inline-flex"
              >
                Sign out
              </Button>
            </div>
          </header>

          <main className="flex-1 px-6 py-8 lg:px-10">{children}</main>
        </div>
      </div>
    </div>
  );
}
