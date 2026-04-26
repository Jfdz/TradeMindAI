"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ArrowRightIcon, BrandMark, CheckIcon, MenuIcon, XIcon } from "@/components/site/icons";
import { footerColumns, publicNavLinks, tickerQuotes } from "@/lib/trademind-content";
import { cn } from "@/lib/utils";

export function TickerBar() {
  const track = [...tickerQuotes, ...tickerQuotes];

  return (
    <div className="sticky top-0 z-50 h-9 overflow-hidden border-b border-border bg-bg-0/95 backdrop-blur-[20px]">
      <div className="group flex h-full items-center overflow-hidden">
        <div className="flex min-w-max animate-marquee items-center gap-8 whitespace-nowrap px-5 text-[11px] uppercase tracking-[0.18em] text-text-2 group-hover:[animation-play-state:paused]">
          {track.map((item, index) => (
            <div className="flex items-center gap-2" key={`${item.pair}-${index}`}>
              <span className="h-1.5 w-1.5 rounded-full bg-cyan animate-pulse-soft" />
              <span className="font-mono text-text-1">{item.pair}</span>
              <span className="font-mono text-text-2">{item.price}</span>
              <span className={cn("font-mono", item.positive ? "text-green" : "text-red")}>{item.change}</span>
            </div>
          ))}
        </div>
        <div className="absolute right-0 h-full w-24 bg-gradient-to-l from-bg-0 to-transparent" />
      </div>
    </div>
  );
}

export function PublicHeader() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  return (
    <header className="sticky top-9 z-40 border-b border-border bg-bg-0/85 backdrop-blur-[20px]">
      <div className="mx-auto flex h-[60px] max-w-7xl items-center justify-between px-5 sm:px-6 lg:px-10">
        <Link className="group flex items-center gap-3" href="/">
          <BrandMark className="h-9 w-9 text-cyan" />
          <div className="leading-none">
            <div className="font-display text-lg font-bold tracking-[-0.04em] text-white">
              TradeMind<span className="text-cyan">AI</span>
            </div>
            <div className="mt-1 text-[10px] uppercase tracking-[0.24em] text-text-3">AI trading intelligence</div>
          </div>
        </Link>

        <nav className="hidden items-center gap-7 lg:flex">
          {publicNavLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={cn("text-sm transition-colors", pathname === link.href ? "text-white" : "text-text-2 hover:text-text-1")}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="hidden items-center gap-3 lg:flex">
          <Button asChild variant="ghost" size="sm">
            <Link href="/auth/login">Log in</Link>
          </Button>
          <Button asChild variant="cyan" size="sm">
            <Link href="/auth/register">
              Start Free
              <ArrowRightIcon className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </div>

        <button
          aria-label="Open navigation"
          className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-border bg-bg-1 text-text-1 lg:hidden"
          onClick={() => setOpen(true)}
          type="button"
        >
          <MenuIcon className="h-5 w-5" />
        </button>
      </div>

      <div
        className={cn("fixed inset-0 z-[60] bg-black/60 transition-opacity lg:hidden", open ? "opacity-100" : "pointer-events-none opacity-0")}
        onClick={() => setOpen(false)}
      />
      <aside
        className={cn(
          "fixed right-0 top-0 z-[70] flex h-full w-[320px] flex-col border-l border-border bg-bg-1 px-5 py-5 shadow-2xl transition-transform lg:hidden",
          open ? "translate-x-0" : "translate-x-full"
        )}
      >
        <div className="flex items-center justify-between">
          <div className="font-display text-lg font-bold tracking-[-0.04em] text-white">
            TradeMind<span className="text-cyan">AI</span>
          </div>
          <button
            aria-label="Close navigation"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-border bg-bg-2 text-text-1"
            onClick={() => setOpen(false)}
            type="button"
          >
            <XIcon className="h-5 w-5" />
          </button>
        </div>

        <nav className="mt-8 space-y-3">
          {publicNavLinks.map((link) => (
            <Link
              key={link.href}
              className={cn(
                "flex items-center justify-between rounded-2xl border px-4 py-3 text-sm transition",
                pathname === link.href ? "border-cyan/30 bg-cyan-dim text-white" : "border-border bg-bg-2 text-text-1 hover:border-border-strong hover:bg-bg-3"
              )}
              href={link.href}
              onClick={() => setOpen(false)}
            >
              {link.label}
              {pathname === link.href ? <CheckIcon className="h-4 w-4 text-cyan" /> : null}
            </Link>
          ))}
        </nav>

        <div className="mt-auto space-y-3">
          <Button asChild className="w-full" variant="cyan">
            <Link href="/auth/register" onClick={() => setOpen(false)}>
              Start Free
              <ArrowRightIcon className="ml-2 h-4 w-4" />
            </Link>
          </Button>
          <Button asChild className="w-full" variant="outline">
            <Link href="/auth/login" onClick={() => setOpen(false)}>
              Log in
            </Link>
          </Button>
        </div>
      </aside>
    </header>
  );
}

export function PublicFooter() {
  return (
    <footer className="border-t border-border bg-bg-1/70">
      <div className="mx-auto max-w-7xl px-5 py-12 sm:px-6 lg:px-10 lg:py-16">
        <div className="grid gap-10 lg:grid-cols-[1.2fr_1fr_1fr_1fr]">
          <div className="max-w-sm">
            <Link className="inline-flex items-center gap-3" href="/">
              <BrandMark className="h-10 w-10 text-cyan" />
              <div className="font-display text-xl font-bold tracking-[-0.05em] text-white">
                TradeMind<span className="text-cyan">AI</span>
              </div>
            </Link>
            <p className="mt-5 text-sm leading-7 text-text-2">
              AI-powered trading intelligence for disciplined signal review, portfolio insight, and faster execution.
            </p>
          </div>

          {footerColumns.map((column) => (
            <div key={column.title}>
              <div className="font-display text-sm font-semibold uppercase tracking-[0.08em] text-white">{column.title}</div>
              <div className="mt-4 space-y-3">
                {column.links.map((link) => (
                  <Link key={link.href} className="block text-sm text-text-2 transition hover:text-text-1" href={link.href}>
                    {link.label}
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="mt-10 flex flex-col gap-4 border-t border-border pt-6 text-sm text-text-2 lg:flex-row lg:items-center lg:justify-between">
          <p>Trading involves significant risk of loss. Not financial advice.</p>
          <p>Copyright 2026 TradeMindAI</p>
        </div>
      </div>
    </footer>
  );
}
