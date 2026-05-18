"use client";

import { RouteError } from "@/components/route-error";

export default function PortfolioAddErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Add Position" title="Unable to load position form" />;
}
