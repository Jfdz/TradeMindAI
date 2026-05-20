# web-app

Next.js 14 dashboard for viewing AI-generated trading signals, managing strategies, running backtests, and visualizing portfolio performance.

## Overview

The user-facing product. Built with Next.js App Router, TypeScript, Tailwind CSS, and shadcn/ui. Fetches real-time signals from trading-core-service, renders candlestick charts with TradingView Lightweight Charts, and supports subscription-gated features.

## Tech Stack

- Next.js 14 (App Router), TypeScript
- Tailwind CSS, shadcn/ui
- TanStack Query v5 (server state)
- Zustand (UI state)
- Clerk (authentication)
- TradingView Lightweight Charts (financial charts)
- react-hook-form + zod (forms)

## Getting Started

### Prerequisites

- Node.js 20+
- npm

### Local development

```bash
cd services/web-app
npm install
cp .env.example .env.local  # Fill in Clerk keys
npm run dev
```

Visit http://localhost:3000

### Environment variables

| Variable | Description | Default |
|---|---|---|
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Clerk publishable key (from Clerk dashboard) | — |
| `CLERK_SECRET_KEY` | Clerk secret key (from Clerk dashboard) | — |
| `NEXT_PUBLIC_CLERK_SIGN_IN_URL` | Sign-in page path | `/auth/login` |
| `NEXT_PUBLIC_CLERK_SIGN_UP_URL` | Sign-up page path | `/auth/register` |
| `NEXT_PUBLIC_CLERK_AFTER_SIGN_IN_URL` | Redirect after sign-in | `/dashboard` |
| `NEXT_PUBLIC_CLERK_AFTER_SIGN_UP_URL` | Redirect after sign-up | `/dashboard` |
| `NEXT_PUBLIC_APP_NAME` | App display name | `TradeMindAI` |
| `API_BASE_URL` | Trading core service URL (server-side only) | `http://localhost:8082` |

## Routes

| Path | Auth | Description |
|---|---|---|
| `/` | No | Landing page |
| `/pricing` | No | Subscription plans |
| `/auth/login` | No | Login |
| `/auth/register` | No | Registration |
| `/dashboard` | Yes | Overview |
| `/dashboard/signals` | Yes | Signals table |
| `/dashboard/portfolio` | Yes | Portfolio |
| `/dashboard/backtests` | Yes | Backtest runner |
| `/dashboard/settings` | Yes | Profile + plan |

## Testing

```bash
npm test                  # Vitest unit tests for middleware, auth, and api-client
```

## Deployment

```bash
docker build -t web-app .
```

## Troubleshooting — stock logos and news thumbnails

Both surface types proxy through trading-core → market-data → Finnhub
(no `FINNHUB_API_KEY` on the web-app pod). If logos drop to initials or
the AI Decision Card never shows a hero news image:

1. Verify `/api/stocks/logos?tickers=AAPL` returns
   `{"AAPL": "https://static2.finnhub.io/..."}` rather than
   `{"AAPL": null}`. A `null` here means the upstream chain failed —
   debug in market-data per its README.
2. Verify `/api/stocks/AAPL` (the SSR enrichment route) returns
   `profile.logo` non-null. Same upstream chain, different proxy
   entrypoint.
3. AI Decision Card without a hero image means the underlying signal's
   `reasoning_artifact.factsSnapshot.news[*]` has no item with a
   non-blank `image` field. Check the ai-engine log for
   `event=reasoning_context.news_no_images ticker=...` warnings.
4. `StockLogo` falls back to an initials chip on `onError`. A flicker
   from logo to initials in the browser usually means a CDN 404 — the
   chain is healthy but Finnhub does not have a logo for that ticker.
