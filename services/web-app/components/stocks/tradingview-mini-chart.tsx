"use client";

import { useEffect, useRef } from "react";

type Props = {
  readonly symbol: string;
  readonly width?: number | string;
  readonly height?: number;
};

export function TradingViewMiniChart({ symbol, width = "100%", height = 220 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const script = document.createElement("script");
    script.src = "https://s3.tradingview.com/external-embedding/embed-widget-mini-symbol-overview.js";
    script.async = true;
    script.innerHTML = JSON.stringify({
      symbol,
      width,
      height,
      locale: "en",
      dateRange: "12M",
      colorTheme: "dark",
      isTransparent: true,
      autosize: false,
      largeChartUrl: "",
    });
    container.appendChild(script);

    return () => {
      const injectedScript = container.querySelector("script");
      if (injectedScript) injectedScript.remove();
      container.innerHTML = "";
    };
  }, [symbol, width, height]);

  return (
    <div className="tradingview-widget-container">
      <div ref={containerRef} />
    </div>
  );
}
