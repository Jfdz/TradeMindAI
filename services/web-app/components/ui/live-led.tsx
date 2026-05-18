"use client";

type LiveLedProps = {
  readonly label?: string;
  readonly size?: "sm" | "md";
};

export function LiveLed({ label = "LIVE", size = "sm" }: LiveLedProps) {
  const dotSize = size === "md" ? "h-2.5 w-2.5" : "h-2 w-2";
  const haloSize = size === "md" ? "h-4 w-4 -top-0.5 -left-0.5" : "h-3 w-3 -top-0.5 -left-0.5";
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="relative inline-block">
        <span className={`${dotSize} block rounded-full bg-emerald-400 animate-pulse`} />
        <span
          className={`${haloSize} absolute rounded-full bg-emerald-400 opacity-30 animate-ping`}
        />
      </span>
      {label && (
        <span className="font-mono text-[10px] uppercase tracking-[0.2em] text-emerald-400">{label}</span>
      )}
    </span>
  );
}
