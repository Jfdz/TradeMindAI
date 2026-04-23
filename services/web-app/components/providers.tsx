"use client";

import { SessionProvider } from "next-auth/react";
import type { ReactNode } from "react";

import { ThemeHydrator } from "@/components/theme/theme-hydrator";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <ThemeHydrator />
      {children}
    </SessionProvider>
  );
}
