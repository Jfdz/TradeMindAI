import type { EarningsEventResponse } from "@/lib/enrichment-client";

type Props = {
  earnings: EarningsEventResponse[];
};

export function EarningsBadge({ earnings }: Props) {
  if (!earnings.length) return null;
  const latest = earnings[0];
  const beat =
    latest.epsActual != null &&
    latest.epsEstimate != null &&
    latest.epsActual > latest.epsEstimate;

  return (
    <div className="rounded-xl border bg-card p-4 space-y-2">
      <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
        Latest Earnings
      </h3>
      <p className="text-sm text-muted-foreground">
        Q{latest.quarter} {latest.year} · {latest.period}
      </p>
      {latest.epsActual != null && (
        <div className="flex items-center gap-2">
          <span
            className={`text-sm font-semibold ${beat ? "text-green-400" : "text-red-400"}`}
          >
            EPS {latest.epsActual.toFixed(2)}
          </span>
          <span className="text-xs text-muted-foreground">
            {beat ? "▲ beat" : "▼ miss"} {latest.epsEstimate?.toFixed(2)} est.
          </span>
        </div>
      )}
    </div>
  );
}
