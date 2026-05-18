"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useState } from "react";

import { ThemeHydrator } from "@/components/theme/theme-hydrator";
import { AuthContext, ClerkAuthBridge } from "@/lib/auth-context";

export function Providers({
  children,
  clerkEnabled,
}: {
  children: ReactNode;
  clerkEnabled: boolean;
}) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  const inner = (
    <QueryClientProvider client={queryClient}>
      <ThemeHydrator />
      {children}
    </QueryClientProvider>
  );

  // ClerkProvider lives in the server root layout (app/layout.tsx) so the
  // publishable key is read at request time, never baked into this client
  // bundle. When Clerk is active, ClerkAuthBridge (a descendant of that
  // ClerkProvider) feeds real user/signOut into AuthContext; otherwise we
  // provide an inert AuthContext so consumers still work.
  if (!clerkEnabled) {
    return (
      <AuthContext.Provider value={{ user: null, signOut: async () => {} }}>
        {inner}
      </AuthContext.Provider>
    );
  }

  return <ClerkAuthBridge>{inner}</ClerkAuthBridge>;
}
