import type { Metadata } from "next";
import type { ReactNode } from "react";
import { IBM_Plex_Mono, Inter, Space_Grotesk } from "next/font/google";
import { ClerkProvider } from "@clerk/nextjs";
import { dark } from "@clerk/themes";
import { Toaster } from "sonner";
import "./globals.css";
import { Providers } from "@/components/providers";

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
  // Server component: process.env is read at request time, so the runtime
  // (k8s/compose) value is always available — it is never baked into the
  // client bundle. The key is forwarded to the client via ClerkProvider's
  // explicit publishableKey prop, so NEXT_PUBLIC_* inlining is not needed.
  const publishableKey = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY ?? "";
  const clerkEnabled = publishableKey.length > 0;

  const body = (
    <body className="relative min-h-screen bg-bg-0 text-text-1 antialiased">
      <div className="pointer-events-none fixed inset-0 z-0 tm-grid-background opacity-[0.45]" />
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_top_left,_rgba(0,200,212,0.12),_transparent_24%),radial-gradient(circle_at_85%_20%,_rgba(232,184,75,0.08),_transparent_24%),linear-gradient(180deg,_rgba(7,9,14,0.96)_0%,_rgba(7,9,14,0.98)_100%)]" />
      <Providers clerkEnabled={clerkEnabled}>
        <div className="relative z-10">{children}</div>
      </Providers>
      <Toaster position="top-center" richColors />
    </body>
  );

  return (
    <html lang="en" suppressHydrationWarning className={`${inter.variable} ${spaceGrotesk.variable} ${ibmPlexMono.variable}`}>
      {clerkEnabled ? (
        <ClerkProvider publishableKey={publishableKey} appearance={clerkAppearance}>
          {body}
        </ClerkProvider>
      ) : (
        body
      )}
    </html>
  );
}
