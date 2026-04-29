"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";

export default function GlobalErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Unhandled client error:", error);
  }, [error]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-bg-0 px-6 text-text-1">
      <div className="max-w-md space-y-6 rounded-2xl border border-border bg-bg-1/80 p-8 text-center shadow-glow">
        <h1 className="font-display text-2xl font-semibold tracking-[-0.04em] text-white">
          Something went wrong
        </h1>
        <p className="text-sm text-text-2">
          The page encountered an error. Try reloading — if the issue persists, contact support.
        </p>
        {error.digest ? (
          <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-text-3">
            Ref: {error.digest}
          </p>
        ) : null}
        <div className="flex justify-center gap-3">
          <Button onClick={reset} variant="default">
            Try again
          </Button>
          <Button onClick={() => window.location.assign("/")} variant="ghost">
            Go home
          </Button>
        </div>
      </div>
    </main>
  );
}
