"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef } from "react";

declare global {
  interface Window {
    TradingView?: {
      widget: new (config: Record<string, unknown>) => unknown;
    };
  }
}

type Props = {
  readonly symbol: string;
  readonly height?: number;
};

function AdvancedChartImpl({ symbol, height = 500 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const containerId = `tv-chart-${symbol.replace(/[^a-z0-9]/gi, "-")}`;
    container.id = containerId;

    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/tv.js";
    script.async = true;
    script.onload = () => {
      if (window.TradingView) {
        new window.TradingView.widget({
          autosize: true,
          height,
          symbol,
          interval: "D",
          timezone: "Etc/UTC",
          theme: "dark",
          style: "1",
          locale: "en",
          backgroundColor: "rgba(0,0,0,0)",
          gridColor: "rgba(255,255,255,0.06)",
          container_id: containerId,
        });
      }
    };
    container.appendChild(script);

    return () => {
      const injectedScript = container.querySelector("script");
      if (injectedScript) injectedScript.remove();
      container.innerHTML = "";
    };
  }, [symbol, height]);

  return <div ref={containerRef} style={{ height }} />;
}

export const TradingViewAdvancedChart = dynamic(
  () => Promise.resolve(AdvancedChartImpl),
  { ssr: false },
);
