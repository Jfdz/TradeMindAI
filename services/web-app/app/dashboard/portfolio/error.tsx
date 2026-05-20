"use client";

import { RouteError } from "@/components/route-error";

export default function PortfolioErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Portfolio" title="Unable to load portfolio" />;
}
