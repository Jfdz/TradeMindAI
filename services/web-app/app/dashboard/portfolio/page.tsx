"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import { apiClient, type MarketSymbolResponse, type PortfolioHoldingResponse, type PortfolioOverviewResponse } from "@/lib/api-client";

type EnrichedHolding = PortfolioHoldingResponse & {
  name: string;
  sector: string;
  color: string;
  trend: number[];
};

const palette = ["#e8b84b", "#60a5fa", "#00d68f", "#ff4d6a", "#c084fc", "#f59e0b"];

function formatMoney(value: number) {
  return value.toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  });
}

function formatSignedMoney(value: number) {
  const formatted = formatMoney(Math.abs(value));
  return value >= 0 ? `+${formatted}` : `-${formatted}`;
}

function Sparkline({ values, color }: { values: number[]; color: string }) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const width = 120;
  const height = 36;

  const points = values
    .map((value, index) => {
      const x = (index / Math.max(values.length - 1, 1)) * width;
      const normalized = max === min ? 0.5 : (value - min) / (max - min);
      const y = height - normalized * height;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <svg className="h-9 w-32" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
      <polyline fill="none" points={points} stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function buildFallbackTrend(lastPrice: number) {
  return Array.from({ length: 10 }, (_, index) => Number((lastPrice * (0.96 + index * 0.01)).toFixed(2)));
}

export default function PortfolioPage() {
  const [portfolio, setPortfolio] = useState<PortfolioOverviewResponse | null>(null);
  const [symbols, setSymbols] = useState<MarketSymbolResponse[]>([]);
  const [holdings, setHoldings] = useState<EnrichedHolding[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadPortfolio() {
      try {
        const [overview, symbolResponse] = await Promise.all([
          apiClient.getPortfolio(),
          apiClient.getSymbols(),
        ]);

        const symbolMap = new Map(symbolResponse.content.map((symbol) => [symbol.ticker, symbol]));
        const enriched = await Promise.all(
          overview.holdings.map(async (holding, index) => {
            const symbol = symbolMap.get(holding.symbol);
            const from = new Date();
            from.setUTCDate(from.getUTCDate() - 10);
            const history = await apiClient.getHistoricalPrices(
              holding.symbol,
              from.toISOString().slice(0, 10),
              new Date().toISOString().slice(0, 10),
              12
            );
            const trend = history.content.length
              ? history.content.slice().reverse().map((bar) => bar.adjustedClose ?? bar.ohlcv.close)
              : buildFallbackTrend(holding.lastPrice);

            return {
              ...holding,
              name: symbol?.name ?? holding.symbol,
              sector: symbol?.sector ?? "Portfolio holding",
              color: palette[index % palette.length],
              trend,
            };
          })
        );

        if (!mounted) {
          return;
        }

        setPortfolio(overview);
        setSymbols(symbolResponse.content);
        setHoldings(enriched);
      } catch (requestError) {
        if (mounted) {
          setError(requestError instanceof Error ? requestError.message : "Unable to load portfolio");
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    }

    loadPortfolio();

    return () => {
      mounted = false;
    };
  }, []);

  const summary = useMemo(() => {
    if (!portfolio) {
      return null;
    }

    const costBasis = portfolio.initialCapital - portfolio.cash;
    return [
      { label: "Total Value", value: formatMoney(portfolio.equity), detail: "Marked to market" },
      { label: "Total Cost Basis", value: formatMoney(costBasis), detail: "Weighted entry cost" },
      { label: "Unrealized P&L", value: formatSignedMoney(portfolio.unrealizedPnl), detail: "Open position gains" },
      { label: "Win Rate", value: `${Math.round(portfolio.winRate * 100)}%`, detail: "Position-level" },
    ];
  }, [portfolio]);

  const allocationGradient = useMemo(() => {
    if (!holdings.length) {
      return "rgba(0,200,212,0.35) 0% 100%";
    }

    let start = 0;
    return holdings
      .map((holding) => {
        const end = start + holding.allocationPct;
        const segment = `${holding.color} ${start}% ${end}%`;
        start = end;
        return segment;
      })
      .join(", ");
  }, [holdings]);

  if (isLoading) {
    return (
      <div className="space-y-8">
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <article key={index} className="h-32 animate-pulse rounded-[20px] bg-bg-1/80" />
          ))}
        </section>
        <section className="grid gap-6 lg:grid-cols-[300px_1fr]">
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
          <div className="h-[420px] animate-pulse rounded-[24px] bg-bg-1/80" />
        </section>
      </div>
    );
  }

  if (error || !portfolio || summary == null) {
    return (
      <section className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-red">Portfolio</div>
        <h2 className="mt-3 font-display text-[clamp(28px,4vw,44px)] font-bold tracking-[-0.05em] text-white">
          Unable to load portfolio
        </h2>
        <p className="mt-3 text-sm leading-7 text-text-2">{error ?? "No portfolio records were returned."}</p>
      </section>
    );
  }

  return (
    <div className="space-y-8">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summary.map((card) => (
          <article key={card.label} className="rounded-[20px] border border-border bg-bg-1/80 p-5 shadow-glow">
            <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">{card.label}</div>
            <div className="mt-3 font-display text-3xl font-bold tracking-[-0.05em] text-white">{card.value}</div>
            <div className="mt-2 text-sm text-text-2">{card.detail}</div>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[300px_1fr]">
        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Portfolio mix</div>
          <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Allocation donut</h3>

          <div className="mt-8 flex items-center justify-center">
            <div className="relative h-48 w-48 rounded-full" style={{ background: `conic-gradient(${allocationGradient})` }}>
              <div className="absolute inset-5 rounded-full border border-border bg-bg-0/95 p-5 text-center">
                <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Portfolio</div>
                <div className="mt-4 font-display text-3xl font-bold tracking-[-0.05em] text-white">
                  {formatMoney(portfolio.equity)}
                </div>
                <div className="mt-2 text-sm text-text-2">Realized {formatSignedMoney(portfolio.realizedPnl)}</div>
              </div>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {holdings.map((position) => (
              <div key={position.symbol} className="flex items-center justify-between rounded-2xl border border-border bg-bg-2 px-4 py-3">
                <div className="flex items-center gap-3">
                  <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                  <div>
                    <div className="text-sm font-semibold text-white">{position.symbol}</div>
                    <div className="text-xs text-text-3">{position.sector}</div>
                  </div>
                </div>
                <div className="font-mono text-sm text-text-1">{position.allocationPct.toFixed(1)}%</div>
              </div>
            ))}
          </div>
        </article>

        <article className="rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Positions</div>
              <h3 className="mt-3 font-display text-2xl font-semibold tracking-[-0.04em] text-white">Open holdings</h3>
            </div>
            <Link
              href="/dashboard/portfolio/add"
              className="inline-flex items-center gap-2 rounded-full bg-cyan px-4 py-2 text-xs font-semibold uppercase tracking-[0.15em] text-black transition-opacity hover:opacity-80"
            >
              + Add Position
            </Link>
          </div>

          <div className="mt-6 overflow-x-auto">
            <table className="min-w-[980px] w-full border-separate border-spacing-0">
              <thead className="text-[11px] uppercase tracking-[0.22em] text-text-3">
                <tr>
                  <th className="px-4 py-3 text-left">Asset</th>
                  <th className="px-4 py-3 text-left">Qty</th>
                  <th className="px-4 py-3 text-left">Avg cost</th>
                  <th className="px-4 py-3 text-left">Current</th>
                  <th className="px-4 py-3 text-left">P&amp;L</th>
                  <th className="px-4 py-3 text-left">P&amp;L %</th>
                  <th className="px-4 py-3 text-left">7d sparkline</th>
                </tr>
              </thead>
              <tbody>
                {holdings.map((position, index) => {
                  const pnl = position.unrealizedPnl;
                  const costBasis = position.quantity * position.averageCost;
                  const pnlPct = costBasis > 0 ? (pnl / costBasis) * 100 : 0;

                  return (
                    <tr key={position.symbol} className={index % 2 === 0 ? "bg-white/[0.015]" : ""}>
                      <td className="border-t border-border px-4 py-4">
                        <div className="flex items-center gap-3">
                          <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                          <div>
                            <div className="font-semibold text-white">{position.symbol}</div>
                            <div className="text-xs text-text-3">{position.name}</div>
                          </div>
                        </div>
                      </td>
                      <td className="border-t border-border px-4 py-4 font-mono text-text-1">{position.quantity}</td>
                      <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.averageCost)}</td>
                      <td className="border-t border-border px-4 py-4 font-mono text-text-1">{formatMoney(position.lastPrice)}</td>
                      <td className={`border-t border-border px-4 py-4 font-mono ${pnl >= 0 ? "text-green" : "text-red"}`}>
                        {formatSignedMoney(pnl)}
                      </td>
                      <td className={`border-t border-border px-4 py-4 font-mono ${pnlPct >= 0 ? "text-green" : "text-red"}`}>
                        {pnlPct >= 0 ? "+" : ""}
                        {pnlPct.toFixed(2)}%
                      </td>
                      <td className="border-t border-border px-4 py-4">
                        <Sparkline values={position.trend} color={position.color} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </div>
  );
}
