import { CandlestickChart } from "@/components/charts/CandlestickChart";
import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";

type SignalChartProps = {
  candles: ChartCandle[];
  marker: ChartMarker;
};

function formatPrice(v: number) {
  return v.toLocaleString("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 2 });
}

function formatVolume(v: number) {
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
  if (v >= 1_000) return `${(v / 1_000).toFixed(0)}K`;
  return String(v);
}

export function SignalChart({ candles, marker }: SignalChartProps) {
  const last5 = candles.slice(-5);
  return (
    <div className="space-y-4">
      <CandlestickChart
        candles={candles}
        markers={[marker]}
        overlays={[
          { kind: "sma", period: 3, color: "#facc15", label: "SMA 3" },
          { kind: "ema", period: 5, color: "#60a5fa", label: "EMA 5" },
        ]}
        showVolume
      />
      {last5.length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-border bg-bg-2/60">
          <table className="min-w-full text-[11px]">
            <thead>
              <tr className="text-text-3 uppercase tracking-[0.18em]">
                <th className="px-3 py-2 text-left">Date</th>
                <th className="px-3 py-2 text-right">Open</th>
                <th className="px-3 py-2 text-right">High</th>
                <th className="px-3 py-2 text-right">Low</th>
                <th className="px-3 py-2 text-right">Close</th>
                <th className="px-3 py-2 text-right">Volume</th>
              </tr>
            </thead>
            <tbody>
              {last5.map((c) => {
                const t = c.time;
                const dateStr = typeof t === "string" ? t : `${t.year}-${String(t.month).padStart(2, "0")}-${String(t.day).padStart(2, "0")}`;
                return (
                  <tr key={dateStr} className="border-t border-border hover:bg-white/[0.025] transition">
                    <td className="px-3 py-2 font-mono text-text-2">{dateStr}</td>
                    <td className="px-3 py-2 font-mono text-right text-text-1">{formatPrice(c.open)}</td>
                    <td className="px-3 py-2 font-mono text-right text-green">{formatPrice(c.high)}</td>
                    <td className="px-3 py-2 font-mono text-right text-red">{formatPrice(c.low)}</td>
                    <td className="px-3 py-2 font-mono text-right text-white">{formatPrice(c.close)}</td>
                    <td className="px-3 py-2 font-mono text-right text-text-2">{formatVolume(c.volume)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
