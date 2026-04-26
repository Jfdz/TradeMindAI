import Link from "next/link";
import type { ReactNode } from "react";

import { PublicHeader, TickerBar } from "@/components/site/site-chrome";
import { cn } from "@/lib/utils";

export function AuthShell({
  mode,
  eyebrow,
  title,
  description,
  children,
}: {
  mode: "login" | "register";
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <main className="min-h-screen bg-bg-0 text-text-1">
      <TickerBar />
      <PublicHeader />

      <section className="relative flex min-h-[calc(100vh-99px)] items-center justify-center px-5 py-12 sm:px-6 lg:px-10">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,_rgba(0,200,212,0.10),_transparent_34%),radial-gradient(circle_at_20%_20%,_rgba(232,184,75,0.08),_transparent_28%)]" />
        <div className="relative w-full max-w-[420px] rounded-[20px] border border-border bg-[linear-gradient(180deg,rgba(12,16,24,0.97),rgba(12,16,24,0.92))] p-7 shadow-glow sm:p-10">
          <div className="h-1 w-full rounded-full bg-gradient-to-r from-cyan via-cyan to-transparent" />

          <div className="mt-6">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">{eyebrow}</div>
            <h1 className="mt-3 font-display text-[clamp(32px,4vw,44px)] font-bold leading-[0.96] tracking-[-0.07em] text-white">
              {title}
            </h1>
            <p className="mt-4 text-sm leading-7 text-text-2">{description}</p>
          </div>

          <div className="mt-8 grid grid-cols-2 rounded-full border border-border bg-bg-2 p-1 text-sm">
            <Link
              className={cn(
                "rounded-full px-3 py-2 text-center transition",
                mode === "login" ? "bg-bg-3 text-white" : "text-text-2 hover:text-text-1"
              )}
              href="/auth/login"
            >
              Log in
            </Link>
            <Link
              className={cn(
                "rounded-full px-3 py-2 text-center transition",
                mode === "register" ? "bg-bg-3 text-white" : "text-text-2 hover:text-text-1"
              )}
              href="/auth/register"
            >
              Create account
            </Link>
          </div>

          <div className="mt-8">{children}</div>
        </div>
      </section>
    </main>
  );
}
