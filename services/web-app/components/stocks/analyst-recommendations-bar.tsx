"use client";

import type { AnalystRecommendationResponse } from "@/lib/enrichment-client";

type Props = {
  recommendations: AnalystRecommendationResponse[];
};

type Segment = { value: number; color: string; label: string };

function buildSegments(rec: AnalystRecommendationResponse): Segment[] {
  return [
    { value: rec.strongBuy, color: "#16a34a", label: "Strong Buy" },
    { value: rec.buy, color: "#4ade80", label: "Buy" },
    { value: rec.hold, color: "#6b7280", label: "Hold" },
    { value: rec.sell, color: "#f87171", label: "Sell" },
    { value: rec.strongSell, color: "#dc2626", label: "Strong Sell" },
  ];
}

export function AnalystRecommendationsBar({ recommendations }: Props) {
  if (!recommendations.length) return null;
  const latest = recommendations[0];
  const segments = buildSegments(latest);
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  if (total === 0) return null;

  let cumulativePct = 0;

  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
        Analyst Consensus · {latest.period}
      </h3>
      <svg
        width="100%"
        height="20"
        className="rounded-sm overflow-hidden"
        aria-label="Analyst recommendation breakdown"
      >
        {segments.map(({ value, color, label }) => {
          const widthPct = (value / total) * 100;
          const x = cumulativePct;
          cumulativePct += widthPct;
          return (
            <rect
              key={label}
              x={`${x}%`}
              y={0}
              width={`${widthPct}%`}
              height={20}
              fill={color}
            >
              <title>{label}: {value}</title>
            </rect>
          );
        })}
      </svg>
      <div className="flex justify-between text-xs">
        <span className="text-green-400">
          Buy {latest.strongBuy + latest.buy}
        </span>
        <span className="text-muted-foreground">Hold {latest.hold}</span>
        <span className="text-red-400">
          Sell {latest.sell + latest.strongSell}
        </span>
      </div>
    </div>
  );
}
