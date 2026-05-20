import Image from "next/image";

export function TradingViewAttribution() {
  return (
    <div className="mt-2 flex justify-center">
      <a
        href="https://www.tradingview.com/"
        rel="noopener noreferrer"
        target="_blank"
        className="group inline-flex items-center gap-2 rounded-full border border-[#2962FF]/45 bg-[linear-gradient(135deg,rgba(13,24,46,0.98),rgba(30,52,92,0.96))] px-4 py-2 text-xs font-semibold tracking-wide text-[#7aa7ff] shadow-[0_0_24px_rgba(41,98,255,0.16)] transition-all duration-200 hover:-translate-y-0.5 hover:border-[#4d86ff]/80 hover:text-[#dce8ff] hover:shadow-[0_0_28px_rgba(41,98,255,0.28)]"
      >
        <Image
          src="/tradingview-logo.svg"
          alt="TradingView"
          width={18}
          height={18}
          className="shrink-0 drop-shadow-[0_0_8px_rgba(41,98,255,0.45)] transition-transform duration-200 group-hover:scale-105"
        />
        <span>Track all markets on TradingView</span>
      </a>
    </div>
  );
}
