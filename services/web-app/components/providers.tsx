"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useState } from "react";

import { ThemeHydrator } from "@/components/theme/theme-hydrator";
import { AuthContext, ClerkAuthBridge } from "@/lib/auth-context";

export function Providers({
  children,
  hasClerk,
}: {
  children: ReactNode;
  hasClerk: boolean;
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

  if (!hasClerk) {
    return (
      <AuthContext.Provider value={{ user: null, signOut: async () => {} }}>
        {inner}
      </AuthContext.Provider>
    );
  }

  return <ClerkAuthBridge>{inner}</ClerkAuthBridge>;
}
