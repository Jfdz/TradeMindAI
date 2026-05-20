"use client";

import { RouteError } from "@/components/route-error";

export default function SignalsErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Signals" title="Unable to load signals" />;
}
