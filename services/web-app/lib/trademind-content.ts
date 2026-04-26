import type { ReactNode } from "react";

export type TickerQuote = {
  pair: string;
  price: string;
  change: string;
  positive: boolean;
};

export type FeatureCard = {
  title: string;
  description: string;
  icon: ReactNode;
};

export type PricingPlan = {
  tier: string;
  name: string;
  price: string;
  tagline: string;
  features: string[];
  cta: string;
  highlighted?: boolean;
};

export type ComparisonRow = {
  feature: string;
  free: string;
  basic: string;
  premium: string;
};

export type FooterColumn = {
  title: string;
  links: { label: string; href: string }[];
};

export type DashboardNavItem = {
  href: string;
  label: string;
  badge?: string;
};

export const publicNavLinks: { href: string; label: string }[] = [
  { href: "/", label: "Home" },
  { href: "/pricing", label: "Pricing" },
  { href: "/dashboard/signals", label: "Signals" },
  { href: "/dashboard", label: "About" },
];

export const tickerQuotes: TickerQuote[] = [
  { pair: "BTC/USDT", price: "68,412.5", change: "+1.42%", positive: true },
  { pair: "ETH/USDT", price: "3,328.4", change: "+0.88%", positive: true },
  { pair: "SOL/USDT", price: "153.9", change: "-0.14%", positive: false },
  { pair: "NVDA", price: "846.20", change: "+2.14%", positive: true },
  { pair: "AAPL", price: "178.50", change: "+0.74%", positive: true },
  { pair: "TSLA", price: "187.80", change: "-1.26%", positive: false },
  { pair: "SPY", price: "512.60", change: "+0.31%", positive: true },
  { pair: "QQQ", price: "448.70", change: "+0.57%", positive: true },
  { pair: "EUR/USD", price: "1.0712", change: "+0.09%", positive: true },
  { pair: "GOLD", price: "2,345.8", change: "-0.22%", positive: false },
];

export const landingFeatures: FeatureCard[] = [
  {
    title: "Live Signal Engine",
    description: "Receive BUY, SELL, and HOLD signals with confidence, timeframe, and risk context at a glance.",
    icon: "zap",
  },
  {
    title: "Strategy Management",
    description: "Keep strategies organized with risk parameters, plan limits, and clear ownership boundaries.",
    icon: "target",
  },
  {
    title: "Portfolio Analytics",
    description: "See exposure, allocation, and P&L in a layout built for fast decision-making.",
    icon: "chart",
  },
  {
    title: "Backtesting Engine",
    description: "Run a strategy against historical data and review metrics, trades, and equity behavior.",
    icon: "flask",
  },
  {
    title: "Smart Notifications",
    description: "Keep traders informed with plan-aware alerts and signal delivery preferences.",
    icon: "bell",
  },
  {
    title: "Risk Controls",
    description: "Enforce subscription limits and position sizing with server-side guardrails.",
    icon: "lock",
  },
];

export const pricingPlans: PricingPlan[] = [
  {
    tier: "FREE",
    name: "Free",
    price: "$0",
    tagline: "5 signals/day · 1 strategy",
    features: ["5 signals per day", "1 active strategy", "Basic market data"],
    cta: "Get started",
  },
  {
    tier: "BASIC",
    name: "Basic",
    price: "$19",
    tagline: "50 signals/day · 5 strategies",
    features: ["50 signals per day", "5 active strategies", "Real-time market data", "Email notifications"],
    cta: "Choose Basic",
    highlighted: true,
  },
  {
    tier: "PREMIUM",
    name: "Premium",
    price: "$49",
    tagline: "Unlimited signals & strategies",
    features: [
      "Unlimited signals",
      "Unlimited strategies",
      "Real-time market data",
      "Priority support",
      "Advanced AI predictions",
      "Backtesting engine",
    ],
    cta: "Go Premium",
  },
];

export const pricingComparisonRows: ComparisonRow[] = [
  { feature: "Trading signals", free: "5", basic: "50", premium: "∞" },
  { feature: "Active strategies", free: "1", basic: "5", premium: "∞" },
  { feature: "Market data", free: "Basic", basic: "Real-time", premium: "Real-time" },
  { feature: "AI predictions", free: "No", basic: "Yes", premium: "Advanced" },
  { feature: "Backtesting", free: "No", basic: "No", premium: "Yes" },
  { feature: "Notifications", free: "Email", basic: "Email", premium: "Email + push" },
  { feature: "Support", free: "Community", basic: "Priority", premium: "Priority" },
  { feature: "Retention", free: "30d", basic: "90d", premium: "Full" },
];

export const footerColumns: FooterColumn[] = [
  {
    title: "Product",
    links: [
      { label: "Signals", href: "/dashboard/signals" },
      { label: "Portfolio", href: "/dashboard/portfolio" },
      { label: "Backtests", href: "/dashboard/backtests" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "Pricing", href: "/pricing" },
      { label: "Login", href: "/auth/login" },
      { label: "Register", href: "/auth/register" },
    ],
  },
  {
    title: "Resources",
    links: [
      { label: "Documentation", href: "/dashboard/settings" },
      { label: "Risk disclosure", href: "/" },
      { label: "Status", href: "/api/health" },
    ],
  },
];

export const dashboardNavItems: DashboardNavItem[] = [
  { href: "/dashboard", label: "Overview" },
  { href: "/dashboard/signals", label: "Signals", badge: "3" },
  { href: "/dashboard/portfolio", label: "Portfolio" },
  { href: "/dashboard/backtests", label: "Backtests" },
  { href: "/dashboard/settings", label: "Settings" },
];
