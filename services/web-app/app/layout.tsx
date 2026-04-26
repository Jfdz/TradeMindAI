import type { Metadata } from "next";
import type { ReactNode } from "react";
import { IBM_Plex_Mono, Inter, Space_Grotesk } from "next/font/google";
import { Toaster } from "sonner";
import "./globals.css";
import { Providers } from "@/components/providers";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
});

const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  variable: "--font-space-grotesk",
});

const ibmPlexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-ibm-plex-mono",
});

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
    <html lang="en" suppressHydrationWarning className={`${inter.variable} ${spaceGrotesk.variable} ${ibmPlexMono.variable}`}>
      <body className="relative min-h-screen bg-bg-0 text-text-1 antialiased">
        <div className="pointer-events-none fixed inset-0 z-0 tm-grid-background opacity-[0.45]" />
        <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top_left,_rgba(0,200,212,0.12),_transparent_24%),radial-gradient(circle_at_85%_20%,_rgba(232,184,75,0.08),_transparent_24%),linear-gradient(180deg,_rgba(7,9,14,0.96)_0%,_rgba(7,9,14,0.98)_100%)]" />
        <Providers>
          <div className="relative z-10">{children}</div>
        </Providers>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  );
}
