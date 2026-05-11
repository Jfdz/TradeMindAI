import Image from "next/image";

type TradingViewAttributionProps = {
  ticker: string;
};

export function TradingViewAttribution({ ticker }: TradingViewAttributionProps) {
  const href = `https://www.tradingview.com/symbols/${ticker}/`;
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-2 rounded-full bg-white px-4 py-1.5 text-[12px] font-semibold text-[#131722] shadow hover:scale-105 transition-transform"
    >
      <Image
        src="/tradingview-logo.svg"
        alt="TradingView"
        width={20}
        height={20}
        className="shrink-0"
      />
      View on TradingView
    </a>
  );
}
