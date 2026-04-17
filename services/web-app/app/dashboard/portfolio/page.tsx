import { PerformanceLineChart } from "@/components/charts/PerformanceLineChart";
import {
  calculateAllocation,
  calculateCostBasis,
  calculatePositionValue,
  calculateUnrealizedPnl,
  formatMoney,
  formatSignedMoney,
  portfolioPositions,
  realizedPnl,
} from "@/lib/dashboard/portfolio";
import { equityCurve } from "@/lib/dashboard/performance";

const portfolioValue = portfolioPositions.reduce((sum, position) => sum + calculatePositionValue(position), 0);
const costBasis = portfolioPositions.reduce((sum, position) => sum + calculateCostBasis(position), 0);
const unrealizedPnl = portfolioValue - costBasis;
const totalPnl = unrealizedPnl + realizedPnl;
const allocationGradient = portfolioPositions
  .map((position, index) => {
    const start = portfolioPositions
      .slice(0, index)
      .reduce((sum, current) => sum + calculateAllocation(current, portfolioValue), 0);
    const end = start + calculateAllocation(position, portfolioValue);
    return `${position.color} ${start}% ${end}%`;
  })
  .join(", ");

export default function PortfolioPage() {
  return (
    <div className="space-y-8">
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <article className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Portfolio value</p>
          <p className="mt-4 text-4xl font-semibold text-white">{formatMoney(portfolioValue)}</p>
          <p className="mt-3 text-sm text-slate-300">Across {portfolioPositions.length} open positions</p>
        </article>
        <article className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Unrealized P&amp;L</p>
          <p className={`mt-4 text-4xl font-semibold ${unrealizedPnl >= 0 ? "text-mint-300" : "text-rose-300"}`}>
            {formatSignedMoney(unrealizedPnl)}
          </p>
          <p className="mt-3 text-sm text-slate-300">Open position gains and losses</p>
        </article>
        <article className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Realized P&amp;L</p>
          <p className="mt-4 text-4xl font-semibold text-gold-300">{formatSignedMoney(realizedPnl)}</p>
          <p className="mt-3 text-sm text-slate-300">Closed trade performance</p>
        </article>
        <article className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Total P&amp;L</p>
          <p className={`mt-4 text-4xl font-semibold ${totalPnl >= 0 ? "text-mint-300" : "text-rose-300"}`}>
            {formatSignedMoney(totalPnl)}
          </p>
          <p className="mt-3 text-sm text-slate-300">Combined unrealized and realized results</p>
        </article>
      </section>

      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Performance</p>
            <h2 className="mt-3 text-2xl font-semibold text-white">Portfolio equity curve</h2>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300">
              The equity line tracks the account value across the last ten sessions, giving a quick read on trend,
              drawdown, and recovery behavior before deeper analysis.
            </p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-ink-800/70 px-5 py-4">
            <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Latest equity</p>
            <p className="mt-2 text-2xl font-semibold text-white">{formatMoney(equityCurve[equityCurve.length - 1].value)}</p>
          </div>
        </div>

        <div className="mt-6 rounded-3xl border border-white/10 bg-ink-800/70 p-4">
          <PerformanceLineChart points={equityCurve} />
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <article className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Positions</p>
              <h2 className="mt-3 text-2xl font-semibold text-white">Open holdings</h2>
            </div>
            <span className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-300">
              Live book
            </span>
          </div>

          <div className="mt-6 overflow-hidden rounded-3xl border border-white/10">
            <table className="min-w-full divide-y divide-white/10">
              <thead className="bg-white/5">
                <tr className="text-left text-xs uppercase tracking-[0.3em] text-slate-400">
                  <th className="px-5 py-4">Symbol</th>
                  <th className="px-5 py-4">Shares</th>
                  <th className="px-5 py-4">Avg cost</th>
                  <th className="px-5 py-4">Last price</th>
                  <th className="px-5 py-4">P&amp;L</th>
                  <th className="px-5 py-4">Sector</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/10 bg-ink-800/50">
                {portfolioPositions.map((position) => {
                  const pnl = calculateUnrealizedPnl(position);

                  return (
                    <tr key={position.symbol} className="text-sm text-slate-200">
                      <td className="px-5 py-4 font-semibold text-white">{position.symbol}</td>
                      <td className="px-5 py-4">{position.shares}</td>
                      <td className="px-5 py-4">{formatMoney(position.averageCost)}</td>
                      <td className="px-5 py-4">{formatMoney(position.lastPrice)}</td>
                      <td className={`px-5 py-4 font-semibold ${pnl >= 0 ? "text-mint-300" : "text-rose-300"}`}>
                        {formatSignedMoney(pnl)}
                      </td>
                      <td className="px-5 py-4 text-slate-300">{position.sector}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </article>

        <article className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Allocation</p>
          <h2 className="mt-3 text-2xl font-semibold text-white">Portfolio mix</h2>

          <div className="mt-6 flex items-center justify-center">
            <div
              className="relative h-72 w-72 rounded-full"
              style={{ background: `conic-gradient(${allocationGradient})` }}
            >
              <div className="absolute inset-[18px] rounded-full border border-white/10 bg-[#08121f] p-6 text-center">
                <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Net value</p>
                <p className="mt-5 text-3xl font-semibold text-white">{formatMoney(portfolioValue)}</p>
                <p className="mt-3 text-sm text-slate-300">Risk balanced across four core names</p>
              </div>
            </div>
          </div>

          <div className="mt-6 space-y-3">
            {portfolioPositions.map((position) => (
              <div key={position.symbol} className="flex items-center justify-between rounded-2xl border border-white/10 bg-ink-800/70 px-4 py-3">
                <div className="flex items-center gap-3">
                  <span className="h-3 w-3 rounded-full" style={{ backgroundColor: position.color }} />
                  <span className="text-sm font-semibold text-white">{position.symbol}</span>
                  <span className="text-xs uppercase tracking-[0.3em] text-slate-400">{position.sector}</span>
                </div>
                <span className="text-sm text-slate-200">
                  {calculateAllocation(position, portfolioValue).toFixed(1)}%
                </span>
              </div>
            ))}
          </div>
        </article>
      </section>
    </div>
  );
}
