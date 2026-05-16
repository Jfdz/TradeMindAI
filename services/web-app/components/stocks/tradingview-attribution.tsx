import Image from "next/image";

export function TradingViewAttribution() {
  return (
    <div className="flex justify-center mt-2">
      <a
        href="https://www.tradingview.com/"
        rel="noopener noreferrer"
        target="_blank"
        className="inline-flex items-center gap-2 rounded-full border border-[#2962FF]/40 bg-[#131722] px-4 py-1.5 text-xs font-medium text-[#2962FF] hover:border-[#2962FF]/70 hover:bg-[#131722]/80 transition-colors"
      >
        <Image
          src="/tradingview-logo.svg"
          alt="TradingView"
          width={16}
          height={16}
          className="shrink-0"
        />
        Track all markets on TradingView
      </a>
    </div>
  );
}
