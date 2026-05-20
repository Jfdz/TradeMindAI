"use client";

import { RouteError } from "@/components/route-error";

export default function BacktestsErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Backtests" title="Unable to load backtests" />;
}
