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
  metadataBase: new URL("https://trademind.es"),
  title: {
    default: "TradeMindAI",
    template: "%s — TradeMindAI",
  },
  description:
    "AI-powered trading signals, portfolio analytics, and backtesting for disciplined traders.",
  openGraph: {
    type: "website",
    siteName: "TradeMindAI",
    title: "TradeMindAI",
    description:
      "AI-powered trading signals, portfolio analytics, and backtesting for disciplined traders.",
    images: [{ url: "/og-image.png", width: 1200, height: 630, alt: "TradeMindAI" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "TradeMindAI",
    description:
      "AI-powered trading signals, portfolio analytics, and backtesting for disciplined traders.",
    images: ["/og-image.png"],
  },
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
