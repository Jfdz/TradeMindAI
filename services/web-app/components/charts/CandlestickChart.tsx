"use client";

import { useEffect, useMemo, useRef } from "react";
import {
  CandlestickSeries,
  createChart,
  createSeriesMarkers,
  HistogramSeries,
  LineSeries,
  type BusinessDay,
  type CandlestickData,
  type HistogramData,
  type LineData,
  type SeriesMarker,
  type Time,
} from "lightweight-charts";

type CandlePoint = {
  time: BusinessDay;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

type OverlayKind = "sma" | "ema";

type OverlayDefinition = {
  kind: OverlayKind;
  period: number;
  color: string;
  label: string;
};

type CandlestickChartProps = {
  candles: CandlePoint[];
  markers?: SeriesMarker<Time>[];
  overlays?: OverlayDefinition[];
  height?: number;
  showVolume?: boolean;
};

const EMPTY_OVERLAYS: OverlayDefinition[] = [];

function computeSma(candles: CandlePoint[], period: number) {
  return candles.map((candle, index) => {
    if (index + 1 < period) {
      return null;
    }

    const window = candles.slice(index + 1 - period, index + 1);
    const sum = window.reduce((total, current) => total + current.close, 0);
    return { time: candle.time, value: sum / period };
  });
}

function computeEma(candles: CandlePoint[], period: number) {
  const multiplier = 2 / (period + 1);
  let ema = candles[0]?.close ?? 0;

  return candles.map((candle, index) => {
    if (index === 0) {
      return { time: candle.time, value: ema };
    }

    ema = (candle.close - ema) * multiplier + ema;
    return { time: candle.time, value: ema };
  });
}

function formatVolume(value: number) {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`;
  }

  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(0)}K`;
  }

  return `${value}`;
}

export function CandlestickChart({
  candles,
  markers,
  overlays = EMPTY_OVERLAYS,
  height = 360,
  showVolume = true,
}: CandlestickChartProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);

  const overlaySeries = useMemo(() => {
    return overlays.flatMap((overlay) => {
      const values = overlay.kind === "sma" ? computeSma(candles, overlay.period) : computeEma(candles, overlay.period);

      return values.length > 0
        ? [
            {
              definition: overlay,
              points: values.filter((point): point is NonNullable<typeof point> => point !== null),
            },
          ]
        : [];
    });
  }, [candles, overlays]);

  useEffect(() => {
    if (!chartRef.current) {
      return;
    }

    const container = chartRef.current;
    const chart = createChart(container, {
      autoSize: false,
      width: container.clientWidth,
      height,
      layout: {
        background: { color: "transparent" },
        textColor: "#dbe4f0",
      },
      grid: {
        vertLines: { color: "rgba(255,255,255,0.06)" },
        horzLines: { color: "rgba(255,255,255,0.06)" },
      },
      rightPriceScale: {
        borderColor: "rgba(255,255,255,0.1)",
      },
      timeScale: {
        borderColor: "rgba(255,255,255,0.1)",
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        vertLine: { color: "rgba(250, 204, 21, 0.5)" },
        horzLine: { color: "rgba(250, 204, 21, 0.5)" },
      },
    });

    const priceSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#34d399",
      downColor: "#fb7185",
      borderVisible: false,
      wickUpColor: "#34d399",
      wickDownColor: "#fb7185",
    });

    priceSeries.setData(candles as CandlestickData<Time>[]);

    const volumeSeries = showVolume
      ? chart.addSeries(HistogramSeries, {
          color: "rgba(250, 204, 21, 0.45)",
          priceScaleId: "volume",
        })
      : null;

    if (volumeSeries) {
      volumeSeries.setData(
        candles.map((candle) => ({
          time: candle.time,
          value: candle.volume,
          color: candle.close >= candle.open ? "rgba(52, 211, 153, 0.45)" : "rgba(251, 113, 133, 0.45)",
        })) as HistogramData<Time>[]
      );
    }

    const overlayLineSeries = overlaySeries.map(({ definition, points }) => {
      const series = chart.addSeries(LineSeries, {
        color: definition.color,
        lineWidth: 2,
      });

      series.setData(points as LineData<Time>[]);
      return series;
    });

    const markerPlugin = markers?.length ? createSeriesMarkers(priceSeries, markers) : null;

    chart.timeScale().fitContent();

    const resizeObserver = new ResizeObserver(() => {
      chart.applyOptions({ width: container.clientWidth });
      chart.timeScale().fitContent();
    });

    resizeObserver.observe(container);

    return () => {
      markerPlugin?.detach();
      resizeObserver.disconnect();
      chart.remove();
      overlayLineSeries.length = 0;
    };
  }, [candles, height, markers, overlaySeries, showVolume]);

  return (
    <div className="space-y-4">
      <div ref={chartRef} className="w-full" style={{ height }} />
      {showVolume ? (
        <div className="rounded-3xl border border-white/10 bg-ink-800/70 p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Volume</p>
            <p className="text-xs uppercase tracking-[0.3em] text-slate-500">Last session bars</p>
          </div>
          <div className="mt-4 flex h-24 items-end gap-2">
            {candles.map((candle) => {
              const maxVolume = Math.max(...candles.map((item) => item.volume), 1);
              const barHeight = Math.max((candle.volume / maxVolume) * 100, 10);

              return (
                <div key={`${candle.time.year}-${candle.time.month}-${candle.time.day}`} className="flex-1">
                  <div
                    className="rounded-t-md"
                    style={{
                      height: `${barHeight}%`,
                      backgroundColor: candle.close >= candle.open ? "rgba(52, 211, 153, 0.7)" : "rgba(251, 113, 133, 0.7)",
                    }}
                    title={formatVolume(candle.volume)}
                  />
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
