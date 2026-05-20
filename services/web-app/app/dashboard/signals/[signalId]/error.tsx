"use client";

import { RouteError } from "@/components/route-error";

export default function SignalDetailErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Signal Detail" title="Unable to load signal" />;
}
