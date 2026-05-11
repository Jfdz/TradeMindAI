import Image from "next/image";

type StockLogoProps = {
  ticker: string;
  symbol?: string;
  logoUrl?: string | null;
  size?: number;
  className?: string;
};

export function StockLogo({ ticker, symbol, logoUrl, size = 32, className }: StockLogoProps) {
  const label = symbol ?? ticker;
  const initials = label.slice(0, 2).toUpperCase();

  if (logoUrl) {
    return (
      <Image
        src={logoUrl}
        alt={label}
        width={size}
        height={size}
        className={className ?? "rounded-full object-contain"}
        unoptimized
      />
    );
  }

  return (
    <span
      className={`inline-flex items-center justify-center rounded-full bg-bg-3 font-mono font-bold text-text-1${className ? ` ${className}` : ""}`}
      style={{ width: size, height: size, fontSize: size * 0.35 }}
      aria-label={label}
    >
      {initials}
    </span>
  );
}
