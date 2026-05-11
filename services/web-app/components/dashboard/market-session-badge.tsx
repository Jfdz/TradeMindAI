"use client";

export function MarketSessionBadge({ latestBarDate }: { readonly latestBarDate: string | null }) {
  if (!latestBarDate) return null;

  const barDay = new Date(latestBarDate);
  const diffDays = Math.floor((Date.now() - barDay.getTime()) / 86_400_000);

  if (diffDays === 0) {
    return (
      <span className="text-[10px] text-cyan uppercase tracking-widest">
        ● OPEN
      </span>
    );
  }

  const label = barDay.toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
  });

  return (
    <span
      className="text-[10px] text-text-3 uppercase tracking-widest"
      title="Last available close"
    >
      CLOSED · last {label}
    </span>
  );
}
