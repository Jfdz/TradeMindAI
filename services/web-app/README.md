# web-app

Next.js 14 dashboard for viewing AI-generated trading signals, managing strategies, running backtests, and visualizing portfolio performance.

## Overview

The user-facing product. Built with Next.js App Router, TypeScript, Tailwind CSS, and shadcn/ui. Fetches real-time signals from trading-core-service, renders candlestick charts with TradingView Lightweight Charts, and supports subscription-gated features.

## Tech Stack

- Next.js 14 (App Router), TypeScript
- Tailwind CSS, shadcn/ui
- TanStack Query v5 (server state)
- Zustand (UI state)
- NextAuth.js (session management)
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
cp ../../.env.example .env.local  # Fill in NEXT_PUBLIC_* vars
npm run dev
```

Visit http://localhost:3000

### Environment variables

| Variable | Description | Default |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Trading core service URL | `http://localhost:8082` |
| `NEXT_PUBLIC_APP_NAME` | App display name | `TradeMindAI` |
| `NEXTAUTH_SECRET` | NextAuth signing secret | — |
| `NEXTAUTH_URL` | Canonical URL for auth callbacks | `http://localhost:3000` |

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
npm test                  # Jest unit tests
npm run test:e2e          # Playwright E2E tests
```

## Deployment

```bash
docker build -t web-app .
```
