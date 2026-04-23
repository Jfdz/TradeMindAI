"use client";

import { useEffect, useRef } from "react";
import { LineSeries, createChart, type LineData, type Time } from "lightweight-charts";

import type { EquityPoint } from "@/lib/dashboard/performance";

type BenchmarkComparisonChartProps = {
  strategyPoints: EquityPoint[];
  benchmarkPoints: EquityPoint[];
  height?: number;
};

export function BenchmarkComparisonChart({ strategyPoints, benchmarkPoints, height = 320 }: BenchmarkComparisonChartProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);

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
    });

    const strategySeries = chart.addSeries(LineSeries, {
      color: "#facc15",
      lineWidth: 3,
    });
    const benchmarkSeries = chart.addSeries(LineSeries, {
      color: "#60a5fa",
      lineWidth: 3,
    });

    strategySeries.setData(strategyPoints as LineData<Time>[]);
    benchmarkSeries.setData(benchmarkPoints as LineData<Time>[]);
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
  }, [benchmarkPoints, height, strategyPoints]);

  return <div ref={chartRef} className="w-full" style={{ height }} />;
}
