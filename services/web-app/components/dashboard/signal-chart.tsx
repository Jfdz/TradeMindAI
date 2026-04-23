import { CandlestickChart } from "@/components/charts/CandlestickChart";
import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";

type SignalChartProps = {
  candles: ChartCandle[];
  marker: ChartMarker;
};

export function SignalChart({ candles, marker }: SignalChartProps) {
  return (
    <CandlestickChart
      candles={candles}
      markers={[marker]}
      overlays={[
        { kind: "sma", period: 3, color: "#facc15", label: "SMA 3" },
        { kind: "ema", period: 5, color: "#60a5fa", label: "EMA 5" },
      ]}
      showVolume
    />
  );
}
