"use client";

import { RouteError } from "@/components/route-error";

export default function SettingsErrorPage({ error, reset }: { readonly error: Error & { readonly digest?: string }; readonly reset: () => void }) {
  return <RouteError error={error} reset={reset} eyebrow="Settings" title="Unable to load settings" />;
}
