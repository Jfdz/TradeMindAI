import type { CompanyProfileResponse } from "@/lib/enrichment-client";
import { StockLogo } from "@/components/ui/stock-logo";

type Props = {
  readonly profile: CompanyProfileResponse | null;
  readonly ticker: string;
};

export function CompanyHeader({ profile, ticker }: Props) {
  return (
    <div className="flex items-center gap-5">
      <StockLogo
        ticker={ticker}
        logoUrl={profile?.logo}
        size={64}
        className="rounded-xl object-contain bg-card border shrink-0"
      />
      <div className="min-w-0">
        <h1 className="font-display font-bold leading-tight tracking-[-0.05em] text-[clamp(28px,4vw,44px)] truncate">
          {profile?.name ?? ticker}
        </h1>
        <p className="text-sm text-muted-foreground">
          {ticker}
          {profile?.exchange ? ` · ${profile.exchange}` : ""}
          {profile?.currency ? ` · ${profile.currency}` : ""}
        </p>
        {profile?.industry && (
          <p className="text-xs text-muted-foreground mt-0.5">{profile.industry}</p>
        )}
      </div>
    </div>
  );
}
