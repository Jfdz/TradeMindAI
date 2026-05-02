"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider, signOut, useSession } from "next-auth/react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";

import { ThemeHydrator } from "@/components/theme/theme-hydrator";

function SessionWatcher() {
  const { data: session } = useSession();
  useEffect(() => {
    if (session?.error === "RefreshAccessTokenError") {
      signOut({ callbackUrl: "/auth/login" });
    }
  }, [session?.error]);
  return null;
}

export function Providers({ children }: { children: ReactNode }) {
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

  return (
    <SessionProvider>
      <SessionWatcher />
      <QueryClientProvider client={queryClient}>
        <ThemeHydrator />
        {children}
      </QueryClientProvider>
    </SessionProvider>
  );
}
