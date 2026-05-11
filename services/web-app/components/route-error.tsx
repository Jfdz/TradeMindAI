"use client";

import { useEffect } from "react";

import { RateLimitBanner } from "@/components/rate-limit-banner";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api-client";

type RouteErrorProps = {
  readonly error: Error & { digest?: string };
  readonly reset: () => void;
  readonly eyebrow: string;
  readonly title: string;
};

export function RouteError({ error, reset, eyebrow, title }: RouteErrorProps) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  if (error instanceof ApiError && error.isRateLimit) {
    return <RateLimitBanner error={error} reset={reset} />;
  }

  return (
    <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">{eyebrow}</div>
      <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">{title}</h2>
      <p className="mt-3 text-sm leading-7 text-text-2">{error.message || "Unexpected error"}</p>
      <Button className="mt-6" onClick={() => reset()} variant="outlineCyan">
        Try again
      </Button>
    </section>
  );
}
