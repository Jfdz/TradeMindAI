import type { CompanyProfileResponse } from "@/lib/enrichment-client";

type Props = {
  readonly profile: CompanyProfileResponse | null;
};

function formatMarketCap(cap: number): string {
  if (cap >= 1e12) return `$${(cap / 1e12).toFixed(2)}T`;
  if (cap >= 1e9) return `$${(cap / 1e9).toFixed(2)}B`;
  if (cap >= 1e6) return `$${(cap / 1e6).toFixed(2)}M`;
  return `$${cap.toLocaleString()}`;
}

export function FundamentalsPanel({ profile }: Props) {
  if (!profile) return null;

  const rows = [
    profile.marketCap != null && { label: "Market Cap", value: formatMarketCap(profile.marketCap) },
    profile.ipo && { label: "IPO Date", value: profile.ipo },
    profile.country && { label: "Country", value: profile.country },
    profile.currency && { label: "Currency", value: profile.currency },
  ].filter(Boolean) as { label: string; value: string }[];

  if (!rows.length) return null;

  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
        Fundamentals
      </h3>
      <dl className="space-y-1.5">
        {rows.map(({ label, value }) => (
          <div key={label} className="flex justify-between items-baseline gap-2">
            <dt className="text-sm text-muted-foreground shrink-0">{label}</dt>
            <dd className="text-sm font-medium text-right truncate">{value}</dd>
          </div>
        ))}
        {profile.weburl && (
          <div className="flex justify-between items-baseline gap-2">
            <dt className="text-sm text-muted-foreground shrink-0">Website</dt>
            <dd className="text-sm font-medium text-right truncate">
              <a
                href={profile.weburl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary hover:underline"
              >
                {profile.weburl.replace(/^https?:\/\//, "").replace(/\/$/, "")}
              </a>
            </dd>
          </div>
        )}
      </dl>
    </div>
  );
}
