import path from "node:path";
import { fileURLToPath } from "node:url";

/** @type {import('next').NextConfig} */
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "same-origin" },
  {
    key: "Strict-Transport-Security",
    value: "max-age=31536000; includeSubDomains; preload",
  },
  {
    key: "Content-Security-Policy",
    value: [
      "default-src 'self'",
      "base-uri 'self'",
      "frame-ancestors 'none'",
      "form-action 'self'",
      "object-src 'none'",
      "img-src 'self' data: blob: https: https://static2.finnhub.io",
      "style-src 'self' 'unsafe-inline'",
      "script-src 'self' 'unsafe-inline' https://s3.tradingview.com https://clerk.trademind.es",
      "frame-src https://*.tradingview.com https://*.clerk.accounts.dev https://clerk.trademind.es https://challenges.cloudflare.com",
      // All browser→API calls go through /api/proxy (same-origin).
      // Clerk's <SignIn /> and SDK make calls to *.clerk.accounts.dev (dev)
      // or the custom Clerk domain (prod). ws:/wss: cover TradingView feeds.
      "connect-src 'self' https://*.clerk.accounts.dev https://clerk.trademind.es https://challenges.cloudflare.com ws: wss: https://s3.tradingview.com http://localhost:* http://127.0.0.1:*",
    ].join("; "),
  },
];

const nextConfig = {
  reactStrictMode: true,
  // outputFileTracingRoot is needed for local monorepo builds but breaks Vercel
  // (Vercel sets VERCEL=1 automatically during its builds)
  ...(process.env.VERCEL !== "1" && { outputFileTracingRoot: workspaceRoot }),
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
