"use client";

import Link from "next/link";

type Props = {
  readonly error: Error;
  readonly reset: () => void;
};

export default function StockErrorComponent({ error, reset }: Props) {
  return (
    <main className="mx-auto max-w-[1280px] space-y-6 px-6 pb-16 pt-8">
      <div className="rounded-xl border bg-card p-8 text-center space-y-4">
        <h2 className="text-lg font-semibold">Failed to load stock data</h2>
        <p className="text-sm text-muted-foreground">
          {error.message || "An unexpected error occurred."}
        </p>
        <div className="flex items-center justify-center gap-3">
          <button
            onClick={reset}
            className="rounded-lg border bg-card px-4 py-2 text-sm hover:bg-accent transition-colors"
          >
            Try again
          </button>
          <Link
            href="/dashboard"
            className="rounded-lg border bg-card px-4 py-2 text-sm hover:bg-accent transition-colors"
          >
            Back to dashboard
          </Link>
        </div>
      </div>
    </main>
  );
}
