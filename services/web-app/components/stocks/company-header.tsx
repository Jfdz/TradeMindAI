import Image from "next/image";
import type { CompanyProfileResponse } from "@/lib/enrichment-client";

type Props = {
  profile: CompanyProfileResponse | null;
  ticker: string;
};

export function CompanyHeader({ profile, ticker }: Props) {
  return (
    <div className="flex items-center gap-5">
      {profile?.logo ? (
        <Image
          src={profile.logo}
          alt={profile.name ?? ticker}
          width={64}
          height={64}
          className="rounded-xl object-contain bg-card border"
          unoptimized
        />
      ) : (
        <div className="w-16 h-16 rounded-xl bg-muted flex items-center justify-center text-xl font-bold shrink-0">
          {ticker.slice(0, 2).toUpperCase()}
        </div>
      )}
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
