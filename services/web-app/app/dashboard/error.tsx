"use client";

import { RouteError } from "@/components/route-error";

export default function DashboardErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Dashboard" title="Unable to load dashboard" />;
}
