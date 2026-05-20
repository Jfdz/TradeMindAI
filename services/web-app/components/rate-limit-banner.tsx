"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import type { ApiError } from "@/lib/api-client";

type Props = {
  readonly error: ApiError;
  readonly reset: () => void;
};

function useCountdown(resetEpoch: number | undefined) {
  const [seconds, setSeconds] = useState(() =>
    resetEpoch ? Math.max(0, Math.ceil((resetEpoch * 1000 - Date.now()) / 1000)) : 0
  );

  useEffect(() => {
    if (!resetEpoch) return;
    const interval = setInterval(() => {
      const remaining = Math.max(0, Math.ceil((resetEpoch * 1000 - Date.now()) / 1000));
      setSeconds(remaining);
      if (remaining === 0) clearInterval(interval);
    }, 1000);
    return () => clearInterval(interval);
  }, [resetEpoch]);

  return seconds;
}

export function RateLimitBanner({ error, reset }: Props) {
  const seconds = useCountdown(error.rateLimit?.resetEpoch);
  const limit = error.rateLimit?.limit;

  return (
    <section className="rounded-[24px] border border-gold/30 bg-[rgba(232,184,75,0.08)] p-6 shadow-glow">
      <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-gold">Free plan limit</div>
      <h2 className="mt-3 font-display text-[clamp(24px,3.5vw,36px)] font-bold tracking-[-0.05em] text-white">
        Rate limit reached
      </h2>
      <p className="mt-3 text-sm leading-7 text-text-2">
        {limit != null ? `Your plan allows ${limit} requests per minute.` : "You've hit the request limit for your plan."}{" "}
        Upgrade to Basic or Premium for higher limits and real-time data.
      </p>
      {seconds > 0 && (
        <p className="mt-2 font-mono text-sm text-gold">
          Resets in {seconds}s
        </p>
      )}
      <div className="mt-6 flex flex-wrap gap-3">
        <Button asChild variant="cyan">
          <Link href="/pricing">View plans</Link>
        </Button>
        <Button variant="outlineCyan" onClick={reset} disabled={seconds > 0}>
          {seconds > 0 ? `Retry in ${seconds}s` : "Try again"}
        </Button>
      </div>
    </section>
  );
}
