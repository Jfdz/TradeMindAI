"use client";

import { ClerkProvider } from "@clerk/nextjs";
import { dark } from "@clerk/themes";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useState } from "react";

import { ThemeHydrator } from "@/components/theme/theme-hydrator";

const CLERK_KEY = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY;

const clerkAppearance = {
  baseTheme: dark,
  variables: {
    colorPrimary: "#22d3ee",
    colorBackground: "#0c1018",
    colorInputBackground: "#131820",
    colorText: "#e2e8f0",
  },
  elements: {
    card: "bg-bg-1 border border-border shadow-glow rounded-[20px]",
    formButtonPrimary: "bg-cyan text-bg-0 hover:bg-cyan/90 rounded-full",
    formFieldInput: "bg-bg-2 border-border text-text-1 rounded-xl",
  },
};

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

  const inner = (
    <QueryClientProvider client={queryClient}>
      <ThemeHydrator />
      {children}
    </QueryClientProvider>
  );

  if (!CLERK_KEY) {
    return inner;
  }

  return (
    <ClerkProvider appearance={clerkAppearance}>
      {inner}
    </ClerkProvider>
  );
}
