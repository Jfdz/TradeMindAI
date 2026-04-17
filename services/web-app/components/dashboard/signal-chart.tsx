"use client";

import { useEffect, useRef } from "react";
import {
  CandlestickSeries,
  createChart,
  createSeriesMarkers,
  type CandlestickData,
  type SeriesMarker,
} from "lightweight-charts";

import type { ChartCandle, ChartMarker } from "@/lib/dashboard/signals";

type SignalChartProps = {
  candles: ChartCandle[];
  marker: ChartMarker;
};

export function SignalChart({ candles, marker }: SignalChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const container = containerRef.current;
    const chart = createChart(container, {
      autoSize: false,
      width: container.clientWidth,
      height: 340,
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

    const series = chart.addSeries(CandlestickSeries, {
      upColor: "#34d399",
      downColor: "#fb7185",
      borderVisible: false,
      wickUpColor: "#34d399",
      wickDownColor: "#fb7185",
    });

    series.setData(candles as CandlestickData[]);
    createSeriesMarkers(series, [marker as SeriesMarker<ChartCandle["time"]>]);
    chart.timeScale().fitContent();

    const resizeObserver = new ResizeObserver(() => {
      chart.applyOptions({ width: container.clientWidth });
      chart.timeScale().fitContent();
    });

    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      chart.remove();
    };
  }, [candles, marker]);

  return <div ref={containerRef} className="h-[340px] w-full" />;
}
