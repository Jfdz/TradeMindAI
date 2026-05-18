"use client";

import { RouteError } from "@/components/route-error";

export default function BacktestDetailErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Backtest Report" title="Unable to load backtest report" />;
}
