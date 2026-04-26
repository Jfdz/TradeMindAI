"use client";

import Link from "next/link";
import { signOut, useSession } from "next-auth/react";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { BrandMark, MenuIcon, XIcon } from "@/components/site/icons";
import { dashboardNavItems } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

const pageTitles: Record<string, string> = {
  "/dashboard": "Overview",
  "/dashboard/signals": "Signals",
  "/dashboard/portfolio": "Portfolio",
  "/dashboard/backtests": "Backtests",
  "/dashboard/settings": "Settings",
};

export function DashboardShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { data: session } = useSession();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const pageTitle = useMemo(() => pageTitles[pathname] ?? "Dashboard", [pathname]);
  const initials = useMemo(() => {
    const source = session?.user?.name || session?.user?.email || "TM";
    return source
      .split(" ")
      .map((part) => part[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
  }, [session?.user?.email, session?.user?.name]);

  return (
    <div className="min-h-screen bg-bg-0 text-text-1">
      <div
        className={cn(
          "fixed inset-0 z-40 bg-black/60 transition-opacity lg:hidden",
          sidebarOpen ? "opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={() => setSidebarOpen(false)}
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-[260px] flex-col border-r border-border bg-bg-1/95 px-5 py-5 backdrop-blur-[20px] transition-transform lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex items-center justify-between">
          <Link className="flex items-center gap-3" href="/dashboard">
            <BrandMark className="h-9 w-9 text-cyan" />
            <div className="leading-none">
              <div className="font-display text-lg font-bold tracking-[-0.04em] text-white">
                TradeMind<span className="text-cyan">AI</span>
              </div>
              <div className="mt-1 text-[10px] uppercase tracking-[0.22em] text-text-3">Dashboard</div>
            </div>
          </Link>
          <button
            aria-label="Close navigation"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-border bg-bg-2 text-text-1 lg:hidden"
            onClick={() => setSidebarOpen(false)}
            type="button"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        <nav className="mt-10 space-y-2">
          {dashboardNavItems.map((item) => {
            const active = pathname === item.href;

            return (
              <Link
                key={item.href}
                className={cn(
                  "flex items-center justify-between rounded-2xl border px-4 py-3 text-sm transition",
                  active ? "border-cyan/30 bg-cyan-dim text-white" : "border-border bg-bg-2 text-text-2 hover:border-border-strong hover:bg-bg-3 hover:text-text-1"
                )}
                href={item.href}
                onClick={() => setSidebarOpen(false)}
              >
                <span>{item.label}</span>
                {item.badge ? (
                  <span className="rounded-full border border-cyan/25 bg-cyan-dim px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan">
                    {item.badge}
                  </span>
                ) : null}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto space-y-4 rounded-[20px] border border-border bg-bg-2 p-4">
          <div className="flex items-center justify-between text-[11px] uppercase tracking-[0.22em] text-text-3">
            <span>{session?.user?.name ?? "Free plan"}</span>
            <span className="rounded-full border border-cyan/25 bg-cyan-dim px-2 py-1 text-cyan">Free</span>
          </div>
          <div>
            <div className="flex items-center justify-between text-xs text-text-2">
              <span>Signals today</span>
              <span className="font-mono text-text-1">3/5</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-bg-3">
              <div className="h-full w-[60%] rounded-full bg-gradient-to-r from-cyan to-cyan/60" />
            </div>
          </div>
          <Button asChild className="w-full" size="sm" variant="outlineCyan">
            <Link href="/pricing">Upgrade plan</Link>
          </Button>
          <div className="flex items-center gap-3 border-t border-border pt-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full border border-cyan/20 bg-cyan-dim font-mono text-xs text-cyan">
              {initials}
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm text-white">{session?.user?.name ?? "TradeMind User"}</div>
              <div className="truncate text-xs text-text-3">{session?.user?.email ?? "user@tradermind.ai"}</div>
            </div>
          </div>
        </div>
      </aside>

      <div className="lg:pl-[260px]">
        <header className="sticky top-0 z-30 border-b border-border bg-bg-0/85 backdrop-blur-[20px]">
          <div className="flex h-[56px] items-center justify-between gap-4 px-5 sm:px-6 lg:px-8">
            <div className="flex items-center gap-3">
              <button
                aria-label="Open navigation"
                className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-border bg-bg-1 text-text-1 lg:hidden"
                onClick={() => setSidebarOpen(true)}
                type="button"
              >
                <MenuIcon className="h-5 w-5" />
              </button>
              <div>
                <div className="text-[11px] uppercase tracking-[0.22em] text-text-3">Dashboard / {pageTitle}</div>
                <div className="mt-1 font-display text-[22px] font-bold tracking-[-0.04em] text-white">{pageTitle}</div>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="hidden items-center gap-2 rounded-full border border-border bg-bg-1 px-3 py-2 text-[11px] uppercase tracking-[0.2em] text-text-2 sm:flex">
                <span className="h-2 w-2 rounded-full bg-green animate-pulse-soft" />
                Live
              </div>
              <Button size="sm" variant="ghost" onClick={() => signOut({ callbackUrl: "/" })}>
                Exit
              </Button>
            </div>
          </div>
        </header>

        <main className="px-5 py-6 sm:px-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}
