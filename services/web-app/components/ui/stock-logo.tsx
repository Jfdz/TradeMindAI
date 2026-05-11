"use client";

import Image from "next/image";
import { useState } from "react";

type StockLogoProps = {
  readonly ticker: string;
  readonly symbol?: string;
  readonly logoUrl?: string | null;
  readonly size?: number;
  readonly className?: string;
};

export function StockLogo({ ticker, symbol, logoUrl, size = 32, className }: StockLogoProps) {
  const [errored, setErrored] = useState(false);
  const label = symbol ?? ticker;
  const initials = label.slice(0, 2).toUpperCase();

  if (logoUrl && !errored) {
    return (
      <Image
        src={logoUrl}
        alt={label}
        width={size}
        height={size}
        className={className ?? "rounded-full object-contain"}
        unoptimized
        onError={() => setErrored(true)}
      />
    );
  }

  return (
    <span
      className={["inline-flex items-center justify-center rounded-full bg-bg-3 font-mono font-bold text-text-1", className].filter(Boolean).join(" ")}
      style={{ width: size, height: size, fontSize: size * 0.35 }}
      aria-label={label}
    >
      {initials}
    </span>
  );
}
