import path from "node:path";
import { fileURLToPath } from "node:url";

/** @type {import('next').NextConfig} */
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

function getOrigin(url) {
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

const apiOrigin = getOrigin(apiBaseUrl);

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
      "img-src 'self' data: blob: https:",
      "style-src 'self' 'unsafe-inline'",
      "script-src 'self'",
      `connect-src 'self' ${apiOrigin ?? "http://localhost:8082"} ws: wss: http://localhost:* http://127.0.0.1:*`,
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
