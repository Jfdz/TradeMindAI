import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Toaster } from "sonner";
import { SpeedInsights } from "@vercel/speed-insights/next";
import "./globals.css";
import { Providers } from "@/components/providers";

export const metadata: Metadata = {
  title: "TradeMindAI",
  description: "Frontend dashboard for trading signals, strategies, and portfolio insights.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
        <Toaster position="top-center" richColors />
        <SpeedInsights />
      </body>
    </html>
  );
}
